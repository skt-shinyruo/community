package com.nowcoder.community.market.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.dto.AddVirtualInventoryBatchRequest;
import com.nowcoder.community.market.dto.CreateVirtualListingRequest;
import com.nowcoder.community.market.dto.VirtualListingResponse;
import com.nowcoder.community.market.dto.VirtualOrderResponse;
import com.nowcoder.community.market.entity.VirtualInventoryUnit;
import com.nowcoder.community.market.entity.VirtualOrder;
import com.nowcoder.community.market.mapper.VirtualInventoryUnitMapper;
import com.nowcoder.community.market.mapper.VirtualListingMapper;
import com.nowcoder.community.market.mapper.VirtualOrderMapper;
import com.nowcoder.community.wallet.api.action.WalletMarketActionApi;
import com.nowcoder.community.wallet.api.model.WalletMarketTxnView;
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

    @Autowired
    private WalletMarketActionApi walletMarketActionApi;

    @Autowired
    private VirtualListingMapper virtualListingMapper;

    @Autowired
    private VirtualInventoryUnitMapper virtualInventoryUnitMapper;

    @Autowired
    private VirtualOrderMapper virtualOrderMapper;

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

    @Test
    void manualOrderShouldRequireSellerDeliveryBeforeBuyerConfirm() {
        seedUserBalance(9, 10_000L);
        long listingId = seedManualListing(7, 1_200L);

        VirtualOrderResponse order = virtualOrderService.createOrder("manual:req-1", 9, listingId, 2);

        assertThat(order.status()).isEqualTo("ESCROWED");

        virtualOrderService.deliverOrder(order.orderId(), 7, "邀请码-A\n邀请码-B");
        VirtualOrderResponse confirmed = virtualOrderService.confirmOrder(order.orderId(), 9);

        assertThat(confirmed.status()).isEqualTo("COMPLETED");
        assertThat(walletTxnCount("virtual-order:" + order.orderId() + ":release")).isEqualTo(1);
        assertThat(walletAccountService.balanceOfUser(7)).isEqualTo(2_400L);
        assertThat(walletAccountService.balanceOfSystem("ORDER_ESCROW")).isZero();
    }

    @Test
    void cancelEscrowedOrderShouldRefundBuyerAndUnlockInventory() {
        seedUserBalance(9, 10_000L);
        long listingId = seedPreloadedListing(7, List.of("CODE-001"));
        VirtualOrderResponse order = seedEscrowedPreloadedOrder("cancel:req-1", 9, listingId, 1);

        VirtualOrderResponse cancelled = virtualOrderService.cancelOrder(order.orderId(), 9);

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(walletTxnCount("virtual-order:" + order.orderId() + ":refund")).isEqualTo(1);
        assertThat(walletAccountService.balanceOfUser(9)).isEqualTo(10_000L);
        assertThat(availableInventoryCount(listingId)).isEqualTo(1);
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

    private long seedManualListing(int sellerUserId, long unitPrice) {
        CreateVirtualListingRequest request = new CreateVirtualListingRequest();
        request.setTitle("邀请码");
        request.setDescription("手工交付");
        request.setUnitPrice(unitPrice);
        request.setDeliveryMode("MANUAL");
        request.setStockMode("FINITE");
        request.setStockTotal(2);
        request.setMinPurchaseQuantity(1);
        request.setMaxPurchaseQuantity(2);

        VirtualListingResponse listing = virtualListingService.createListing(sellerUserId, request, null);
        return listing.listingId();
    }

    private VirtualOrderResponse seedEscrowedPreloadedOrder(String requestId, int buyerUserId, long listingId, int quantity) {
        var listing = virtualListingMapper.selectById(listingId);
        long totalAmount = listing.getUnitPrice() * quantity;
        WalletMarketTxnView escrowTxn = walletMarketActionApi.escrowOrder(
                "virtual-order:" + requestId + ":seed:escrow",
                buyerUserId,
                totalAmount,
                "virtual-order:" + requestId
        );

        VirtualOrder order = new VirtualOrder();
        order.setRequestId("seed:" + requestId);
        order.setListingId(listingId);
        order.setSellerUserId(listing.getSellerUserId());
        order.setBuyerUserId(buyerUserId);
        order.setQuantity(quantity);
        order.setUnitPriceSnapshot(listing.getUnitPrice());
        order.setTotalAmount(totalAmount);
        order.setDeliveryModeSnapshot(listing.getDeliveryMode());
        order.setListingTitleSnapshot(listing.getTitle());
        order.setStatus("ESCROWED");
        order.setEscrowTxnId(escrowTxn.txnId());
        virtualOrderMapper.insert(order);

        for (VirtualInventoryUnit unit : virtualInventoryUnitMapper.selectAvailableForUpdate(listingId, quantity)) {
            virtualInventoryUnitMapper.reserveForOrder(unit.getInventoryUnitId(), order.getOrderId());
        }
        virtualListingMapper.adjustStock(listingId, listing.getSellerUserId(), 0, -quantity, "SOLD_OUT");
        return VirtualOrderResponse.from(virtualOrderMapper.selectById(order.getOrderId()));
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

    private int availableInventoryCount(long listingId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from virtual_inventory_unit where listing_id = ? and status = 'AVAILABLE'",
                Integer.class,
                listingId
        );
        return count == null ? 0 : count;
    }
}
