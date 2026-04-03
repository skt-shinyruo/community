package com.nowcoder.community.infra.job.handlers;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.dto.CreateVirtualListingRequest;
import com.nowcoder.community.market.dto.VirtualListingResponse;
import com.nowcoder.community.market.dto.VirtualOrderResponse;
import com.nowcoder.community.market.service.VirtualListingService;
import com.nowcoder.community.market.service.VirtualOrderService;
import com.nowcoder.community.wallet.service.WalletAccountService;
import com.xxl.job.core.context.XxlJobContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class VirtualOrderAutoReleaseHandlerTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VirtualListingService virtualListingService;

    @Autowired
    private VirtualOrderService virtualOrderService;

    @Autowired
    private VirtualOrderAutoReleaseHandler handler;

    @Autowired
    private WalletAccountService walletAccountService;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from virtual_dispute");
        jdbcTemplate.update("delete from virtual_delivery");
        jdbcTemplate.update("delete from virtual_order");
        jdbcTemplate.update("delete from virtual_inventory_unit");
        jdbcTemplate.update("delete from virtual_listing");
        jdbcTemplate.update("delete from reward_ledger");
        jdbcTemplate.update("delete from reward_account");
        jdbcTemplate.update("delete from wallet_admin_action");
        jdbcTemplate.update("delete from wallet_entry");
        jdbcTemplate.update("delete from wallet_txn");
        jdbcTemplate.update("delete from recharge_order");
        jdbcTemplate.update("delete from withdraw_order");
        jdbcTemplate.update("delete from transfer_order");
        jdbcTemplate.update("delete from wallet_account");
        XxlJobContext.setXxlJobContext(new XxlJobContext(1L, "", 2L, System.currentTimeMillis(), "", 0, 1));
    }

    @AfterEach
    void tearDown() {
        XxlJobContext.setXxlJobContext(null);
    }

    @Test
    void autoReleaseHandlerShouldCompleteOverdueDeliveredOrders() {
        seedUserBalance(9, 10_000L);
        long orderId = seedDeliveredOrderDueForAutoRelease();

        handler.autoRelease();

        assertThat(orderStatus(orderId)).isEqualTo("COMPLETED");
        assertThat(walletTxnCount("virtual-order:" + orderId + ":release")).isEqualTo(1);
        assertThat(XxlJobContext.getXxlJobContext().getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
    }

    private long seedDeliveredOrderDueForAutoRelease() {
        CreateVirtualListingRequest request = new CreateVirtualListingRequest();
        request.setTitle("邀请码");
        request.setDescription("手工交付");
        request.setUnitPrice(1_200L);
        request.setDeliveryMode("MANUAL");
        request.setStockMode("FINITE");
        request.setStockTotal(2);
        request.setMinPurchaseQuantity(1);
        request.setMaxPurchaseQuantity(2);

        VirtualListingResponse listing = virtualListingService.createListing(7, request, null);
        VirtualOrderResponse order = virtualOrderService.createOrder("auto-release:req-1", 9, listing.listingId(), 2);
        virtualOrderService.deliverOrder(order.orderId(), 7, "邀请码-A\n邀请码-B");
        jdbcTemplate.update(
                "update virtual_order set auto_confirm_at = timestampadd('HOUR', -1, current_timestamp) where order_id = ?",
                order.orderId()
        );
        return order.orderId();
    }

    private void seedUserBalance(int userId, long balance) {
        long accountId = walletAccountService.ensureUserWallet(userId);
        jdbcTemplate.update(
                "update wallet_account set balance = ?, version = 0, status = 'ACTIVE' where account_id = ?",
                balance,
                accountId
        );
    }

    private String orderStatus(long orderId) {
        return jdbcTemplate.queryForObject(
                "select status from virtual_order where order_id = ?",
                String.class,
                orderId
        );
    }

    private int walletTxnCount(String requestId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from wallet_txn where request_id = ?",
                Integer.class,
                requestId
        );
        return count == null ? 0 : count;
    }
}
