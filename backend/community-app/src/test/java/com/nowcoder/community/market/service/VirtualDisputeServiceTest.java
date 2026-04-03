package com.nowcoder.community.market.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.dto.CreateVirtualListingRequest;
import com.nowcoder.community.market.dto.CreateVirtualDisputeRequest;
import com.nowcoder.community.market.dto.VirtualDisputeResponse;
import com.nowcoder.community.market.dto.VirtualListingResponse;
import com.nowcoder.community.market.dto.VirtualOrderResponse;
import com.nowcoder.community.wallet.service.WalletAccountService;
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
class VirtualDisputeServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VirtualListingService virtualListingService;

    @Autowired
    private VirtualOrderService virtualOrderService;

    @Autowired
    private VirtualDisputeService virtualDisputeService;

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
    }

    @Test
    void sellerAcceptedDisputeShouldRefundBuyer() {
        seedUserBalance(9, 10_000L);
        long orderId = seedDeliveredManualOrder(7, 9, 2_400L);

        CreateVirtualDisputeRequest request = new CreateVirtualDisputeRequest();
        request.setReason("商品无效");
        request.setBuyerNote("兑换失败");

        VirtualDisputeResponse dispute = virtualDisputeService.openDispute(orderId, 9, request.getReason(), request.getBuyerNote());
        VirtualDisputeResponse resolved = virtualDisputeService.sellerAcceptRefund(dispute.disputeId(), 7, "同意退款");

        assertThat(resolved.status()).isEqualTo("SELLER_ACCEPTED");
        assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
        assertThat(walletTxnCount("virtual-order:" + orderId + ":refund")).isEqualTo(1);
        assertThat(walletAccountService.balanceOfUser(9)).isEqualTo(10_000L);
    }

    private long seedDeliveredManualOrder(int sellerUserId, int buyerUserId, long totalAmount) {
        CreateVirtualListingRequest request = new CreateVirtualListingRequest();
        request.setTitle("邀请码");
        request.setDescription("手工交付");
        request.setUnitPrice(totalAmount / 2);
        request.setDeliveryMode("MANUAL");
        request.setStockMode("FINITE");
        request.setStockTotal(2);
        request.setMinPurchaseQuantity(1);
        request.setMaxPurchaseQuantity(2);

        VirtualListingResponse listing = virtualListingService.createListing(sellerUserId, request, null);
        VirtualOrderResponse order = virtualOrderService.createOrder("dispute:req-1", buyerUserId, listing.listingId(), 2);
        virtualOrderService.deliverOrder(order.orderId(), sellerUserId, "邀请码-A\n邀请码-B");
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
