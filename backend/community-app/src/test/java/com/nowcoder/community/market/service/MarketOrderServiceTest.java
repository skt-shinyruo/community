package com.nowcoder.community.market.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.dto.CreateMarketAddressRequest;
import com.nowcoder.community.market.dto.CreateMarketListingRequest;
import com.nowcoder.community.market.dto.MarketOrderDetailResponse;
import com.nowcoder.community.market.dto.MarketOrderResponse;
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
class MarketOrderServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketOrderService marketOrderService;

    @Autowired
    private MarketListingService marketListingService;

    @Autowired
    private MarketAddressService marketAddressService;

    @Autowired
    private MarketQueryService marketQueryService;

    @Autowired
    private WalletAccountService walletAccountService;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from market_shipment");
        jdbcTemplate.update("delete from market_dispute");
        jdbcTemplate.update("delete from market_order");
        jdbcTemplate.update("delete from market_inventory_unit");
        jdbcTemplate.update("delete from market_address");
        jdbcTemplate.update("delete from market_listing");
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
    void createPhysicalOrderShouldSnapshotSelectedAddressAndStayEscrowedBeforeShipment() {
        long listingId = seedPhysicalListing(7);
        long addressId = seedAddress(9, true);
        seedBuyerBalance(9, 20_000L);

        MarketOrderResponse response = marketOrderService.createOrder("physical:req-1", 9, listingId, 1, addressId);

        assertThat(response.goodsType()).isEqualTo("PHYSICAL");
        assertThat(response.status()).isEqualTo("ESCROWED");
        assertThat(response.autoConfirmAt()).isNull();

        MarketOrderDetailResponse detail = marketQueryService.getOrderDetail(response.orderId(), 9);
        assertThat(detail.receiverNameSnapshot()).isEqualTo("张三");
        assertThat(detail.shipment()).isNull();
    }

    @Test
    void buyerAndSellerQueriesShouldReturnUnifiedMixedOrderSnapshots() {
        seedBuyerBalance(9, 40_000L);
        long virtualOrderId = seedDeliveredVirtualOrder(7, 9);
        long physicalOrderId = seedEscrowedPhysicalOrder(8, 9);

        assertThat(marketQueryService.listBuyingOrders(9))
                .extracting(MarketOrderResponse::goodsType)
                .contains("VIRTUAL", "PHYSICAL");
        assertThat(marketQueryService.listSellingOrders(8))
                .extracting(MarketOrderResponse::orderId)
                .contains(physicalOrderId);
        assertThat(marketQueryService.getOrderDetail(virtualOrderId, 9).deliveryContents())
                .containsExactly("CODE-001");
    }

    @Test
    void deliverVirtualOrderShouldPersistManualDeliveryContent() {
        seedBuyerBalance(9, 20_000L);
        long orderId = seedEscrowedVirtualOrder(7, 9);

        MarketOrderResponse delivered = marketOrderService.deliverVirtualOrder(orderId, 7, "CODE-001");

        assertThat(delivered.status()).isEqualTo("DELIVERED");
        assertThat(delivered.autoConfirmAt()).isNotNull();
        assertThat(marketQueryService.getOrderDetail(orderId, 9).deliveryContents()).containsExactly("CODE-001");
    }

    @Test
    void confirmDeliveredVirtualOrderShouldReleaseEscrowToSeller() {
        seedBuyerBalance(9, 20_000L);
        long orderId = seedEscrowedVirtualOrder(7, 9);
        marketOrderService.deliverVirtualOrder(orderId, 7, "CODE-001");

        MarketOrderResponse confirmed = marketOrderService.confirmOrder(orderId, 9);

        assertThat(confirmed.status()).isEqualTo("COMPLETED");
        assertThat(balanceOfUser(7)).isEqualTo(1_200L);
    }

    @Test
    void shipPhysicalOrderShouldPersistShipmentAndSetSevenDayAutoConfirm() {
        seedBuyerBalance(9, 20_000L);
        long orderId = seedEscrowedPhysicalOrder(7, 9);

        MarketOrderResponse shipped = marketOrderService.shipPhysicalOrder(orderId, 7, "顺丰", "SF1234567890", "工作日派送");

        assertThat(shipped.status()).isEqualTo("SHIPPED");
        assertThat(shipped.autoConfirmAt()).isNotNull();
        assertThat(marketQueryService.getOrderDetail(orderId, 9).shipment().trackingNo()).isEqualTo("SF1234567890");
    }

    @Test
    void cancelPhysicalOrderBeforeShipmentShouldRefundBuyer() {
        seedBuyerBalance(9, 20_000L);
        long orderId = seedEscrowedPhysicalOrder(7, 9);

        MarketOrderResponse cancelled = marketOrderService.cancelOrder(orderId, 9);

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(balanceOfUser(9)).isEqualTo(20_000L);
    }

    private long seedDeliveredVirtualOrder(int sellerUserId, int buyerUserId) {
        CreateMarketListingRequest request = new CreateMarketListingRequest();
        request.setGoodsType("VIRTUAL");
        request.setTitle("Steam 兑换码");
        request.setDescription("自动交付");
        request.setUnitPrice(1_999L);
        request.setDeliveryMode("PRELOADED");
        request.setStockMode("FINITE");
        request.setStockTotal(1);
        request.setMinPurchaseQuantity(1);
        request.setMaxPurchaseQuantity(1);

        var inventory = new com.nowcoder.community.market.dto.AddMarketInventoryBatchRequest();
        inventory.setPayloadType("CODE");
        inventory.setPayloads(List.of("CODE-001"));

        long listingId = marketListingService.createListing(sellerUserId, request, inventory).listingId();
        return marketOrderService.createOrder("virtual:req-1", buyerUserId, listingId, 1, null).orderId();
    }

    private long seedEscrowedVirtualOrder(int sellerUserId, int buyerUserId) {
        CreateMarketListingRequest request = new CreateMarketListingRequest();
        request.setGoodsType("VIRTUAL");
        request.setTitle("邀请码");
        request.setDescription("手工交付");
        request.setUnitPrice(1_200L);
        request.setDeliveryMode("MANUAL");
        request.setStockMode("FINITE");
        request.setStockTotal(2);
        request.setMinPurchaseQuantity(1);
        request.setMaxPurchaseQuantity(2);

        long listingId = marketListingService.createListing(sellerUserId, request, null).listingId();
        return marketOrderService.createOrder("virtual:manual:req-1", buyerUserId, listingId, 1, null).orderId();
    }

    private long seedEscrowedPhysicalOrder(int sellerUserId, int buyerUserId) {
        long listingId = seedPhysicalListing(sellerUserId);
        long addressId = seedAddress(buyerUserId, true);
        return marketOrderService.createOrder("physical:req-2", buyerUserId, listingId, 1, addressId).orderId();
    }

    private long seedPhysicalListing(int sellerUserId) {
        CreateMarketListingRequest request = new CreateMarketListingRequest();
        request.setGoodsType("PHYSICAL");
        request.setTitle("二手键盘");
        request.setDescription("九成新");
        request.setUnitPrice(12_900L);
        request.setStockTotal(3);
        request.setMinPurchaseQuantity(1);
        request.setMaxPurchaseQuantity(1);
        return marketListingService.createListing(sellerUserId, request, null).listingId();
    }

    private long seedAddress(int userId, boolean isDefault) {
        CreateMarketAddressRequest request = new CreateMarketAddressRequest();
        request.setReceiverName("张三");
        request.setReceiverPhone("13800000000");
        request.setProvince("上海市");
        request.setCity("上海市");
        request.setDistrict("浦东新区");
        request.setDetailAddress("世纪大道 100 号");
        request.setPostalCode("200120");
        request.setDefault(isDefault);
        return marketAddressService.createAddress(userId, request).addressId();
    }

    private void seedBuyerBalance(int userId, long balance) {
        long accountId = walletAccountService.ensureUserWallet(userId);
        jdbcTemplate.update(
                "update wallet_account set balance = ?, version = 0, status = 'ACTIVE' where account_id = ?",
                balance,
                accountId
        );
    }

    private long balanceOfUser(int userId) {
        return walletAccountService.balanceOfUser(userId);
    }
}
