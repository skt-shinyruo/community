package com.nowcoder.community.market.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.dto.AddVirtualInventoryBatchRequest;
import com.nowcoder.community.market.dto.CreateVirtualListingRequest;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class VirtualOrderServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VirtualOrderService virtualOrderService;

    @Autowired
    private VirtualListingService virtualListingService;

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
    void createPreloadedOrderShouldEscrowAndAutoDeliverReservedInventory() {
        seedUserBalance(9, 10_000L);
        long listingId = seedPreloadedListing(7, List.of("CODE-001", "CODE-002"));

        VirtualOrderResponse response = virtualOrderService.createOrder("market-order:req-1", 9, listingId, 2);

        assertThat(response.status()).isEqualTo("DELIVERED");
        assertThat(response.totalAmount()).isEqualTo(3_998L);
        assertThat(walletTxnCount("market-order:req-1:escrow")).isEqualTo(1);
        assertThat(deliveryContentOf(response.orderId())).contains("CODE-001", "CODE-002");
        assertThat(walletAccountService.balanceOfUser(9)).isEqualTo(6_002L);
        assertThat(walletAccountService.balanceOfSystem("ORDER_ESCROW")).isEqualTo(3_998L);
    }

    private long seedPreloadedListing(int sellerUserId, List<String> payloads) {
        CreateVirtualListingRequest request = new CreateVirtualListingRequest();
        request.setTitle("Steam 兑换码");
        request.setDescription("自动交付");
        request.setUnitPrice(1_999L);
        request.setDeliveryMode("PRELOADED");
        request.setStockMode("FINITE");
        request.setStockTotal(payloads.size());
        request.setMinPurchaseQuantity(1);
        request.setMaxPurchaseQuantity(payloads.size());

        AddVirtualInventoryBatchRequest inventory = new AddVirtualInventoryBatchRequest();
        inventory.setPayloadType("CODE");
        inventory.setPayloads(payloads);

        VirtualListingResponse listing = virtualListingService.createListing(sellerUserId, request, inventory);
        return listing.listingId();
    }

    private void seedUserBalance(int userId, long balance) {
        long accountId = walletAccountService.ensureUserWallet(userId);
        jdbcTemplate.update(
                "update wallet_account set balance = ?, version = 0, status = 'ACTIVE' where account_id = ?",
                balance,
                accountId
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

    private String deliveryContentOf(long orderId) {
        return jdbcTemplate.queryForObject(
                "select delivery_content from virtual_delivery where order_id = ?",
                String.class,
                orderId
        );
    }
}
