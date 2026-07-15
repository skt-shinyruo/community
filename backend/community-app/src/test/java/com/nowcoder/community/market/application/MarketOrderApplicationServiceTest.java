package com.nowcoder.community.market.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.ErrorKind;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.controller.dto.CreateMarketAddressRequest;
import com.nowcoder.community.market.controller.dto.CreateMarketListingRequest;
import com.nowcoder.community.market.application.result.MarketOrderDetailResult;
import com.nowcoder.community.market.application.result.MarketOrderResult;
import com.nowcoder.community.wallet.application.WalletAccountApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.nowcoder.community.support.TestUuids.uuid;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class MarketOrderApplicationServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketOrderApplicationService marketOrderService;

    @Autowired
    private MarketWalletActionProcessorApplicationService marketWalletActionProcessor;

    @Autowired
    private MarketListingApplicationService marketListingService;

    @Autowired
    private MarketAddressApplicationService marketAddressService;

    @Autowired
    private MarketQueryApplicationService marketQueryService;

    @Autowired
    private WalletAccountApplicationService walletAccountService;

    @Autowired
    private DataSource dataSource;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from market_shipment");
        jdbcTemplate.update("delete from market_dispute");
        jdbcTemplate.update("delete from market_wallet_action");
        jdbcTemplate.update("delete from market_order");
        jdbcTemplate.update("delete from market_inventory_unit");
        jdbcTemplate.update("delete from market_address");
        jdbcTemplate.update("delete from market_listing");
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
        UUID sellerUserId = uuid(7);
        UUID buyerUserId = uuid(9);
        UUID listingId = seedPhysicalListing(sellerUserId);
        UUID addressId = seedAddress(buyerUserId, true);
        seedBuyerBalance(buyerUserId, 20_000L);

        MarketOrderResult response = marketOrderService.createOrder("physical:req-1", buyerUserId, listingId, 1, addressId);

        assertThat(response.goodsType()).isEqualTo("PHYSICAL");
        assertThat(response.status()).isEqualTo("ESCROW_PENDING");
        assertThat(response.autoConfirmAt()).isNull();
        assertThat(countRows("wallet_txn")).isZero();

        marketWalletActionProcessor.processDue(10);

        MarketOrderDetailResult detail = marketQueryService.getOrderDetail(response.orderId(), buyerUserId);
        assertThat(detail.status()).isEqualTo("ESCROWED");
        assertThat(detail.receiverNameSnapshot()).isEqualTo("张三");
        assertThat(detail.shipment()).isNull();
    }

    @Test
    void createOrderShouldReturnEscrowPendingAndProcessorShouldEscrow() {
        UUID sellerUserId = uuid(7);
        UUID buyerUserId = uuid(9);
        UUID listingId = seedPhysicalListing(sellerUserId);
        UUID addressId = seedAddress(buyerUserId, true);
        seedBuyerBalance(buyerUserId, 20_000L);

        MarketOrderResult created = marketOrderService.createOrder("physical:req-pending", buyerUserId, listingId, 1, addressId);

        assertThat(created.status()).isEqualTo("ESCROW_PENDING");
        assertThat(countRows("wallet_txn")).isZero();

        marketWalletActionProcessor.processDue(10);

        MarketOrderDetailResult detail = marketQueryService.getOrderDetail(created.orderId(), buyerUserId);
        assertThat(detail.status()).isEqualTo("ESCROWED");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from wallet_txn where request_id = ?",
                Integer.class,
                "market-order:" + created.orderId() + ":escrow"
        )).isEqualTo(1);
    }

    @Test
    void createPhysicalOrderShouldPersistNullDeliveryModeSnapshotWhenListingDeliveryModeIsNull() {
        UUID sellerUserId = uuid(7);
        UUID buyerUserId = uuid(9);
        UUID listingId = seedPhysicalListing(sellerUserId);
        UUID addressId = seedAddress(buyerUserId, true);
        seedBuyerBalance(buyerUserId, 20_000L);

        MarketOrderResult created = marketOrderService.createOrder(
                "physical:req-null-delivery-mode",
                buyerUserId,
                listingId,
                1,
                addressId
        );

        assertThat(jdbcTemplate.queryForObject(
                "select delivery_mode_snapshot from market_order where order_id = ?",
                String.class,
                created.orderId()
        )).isNull();
    }

    @Test
    void buyerAndSellerQueriesShouldReturnUnifiedMixedOrderSnapshots() {
        UUID firstSellerUserId = uuid(7);
        UUID secondSellerUserId = uuid(8);
        UUID buyerUserId = uuid(9);
        seedBuyerBalance(buyerUserId, 40_000L);
        UUID virtualOrderId = seedDeliveredVirtualOrder(firstSellerUserId, buyerUserId);
        UUID physicalOrderId = seedEscrowedPhysicalOrder(secondSellerUserId, buyerUserId);

        assertThat(marketQueryService.listBuyingOrders(buyerUserId))
                .extracting(MarketOrderResult::goodsType)
                .contains("VIRTUAL", "PHYSICAL");
        assertThat(marketQueryService.listSellingOrders(secondSellerUserId))
                .extracting(MarketOrderResult::orderId)
                .contains(physicalOrderId);
        assertThat(marketQueryService.getOrderDetail(virtualOrderId, buyerUserId).deliveryContents())
                .containsExactly("CODE-001");
    }

    @Test
    void deliverVirtualOrderShouldPersistManualDeliveryContent() {
        UUID sellerUserId = uuid(7);
        UUID buyerUserId = uuid(9);
        seedBuyerBalance(buyerUserId, 20_000L);
        UUID orderId = seedEscrowedVirtualOrder(sellerUserId, buyerUserId);

        MarketOrderResult delivered = marketOrderService.deliverVirtualOrder(orderId, sellerUserId, "CODE-001");

        assertThat(delivered.status()).isEqualTo("DELIVERED");
        assertThat(delivered.autoConfirmAt()).isNotNull();
        assertThat(marketQueryService.getOrderDetail(orderId, buyerUserId).deliveryContents()).containsExactly("CODE-001");
    }

    @Test
    void confirmDeliveredVirtualOrderShouldReleaseEscrowToSeller() {
        UUID sellerUserId = uuid(7);
        UUID buyerUserId = uuid(9);
        seedBuyerBalance(buyerUserId, 20_000L);
        UUID orderId = seedEscrowedVirtualOrder(sellerUserId, buyerUserId);
        marketOrderService.deliverVirtualOrder(orderId, sellerUserId, "CODE-001");

        MarketOrderResult confirmed = marketOrderService.confirmOrder(orderId, buyerUserId);

        assertThat(confirmed.status()).isEqualTo("RELEASE_PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from market_wallet_action where request_id = ?",
                Integer.class,
                "market-order:" + orderId + ":release"
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from wallet_txn where request_id = ?",
                Integer.class,
                "market-order:" + orderId + ":release"
        )).isZero();

        marketWalletActionProcessor.processDue(10);

        assertThat(marketQueryService.getOrderDetail(orderId, buyerUserId).status()).isEqualTo("COMPLETED");
        assertThat(balanceOfUser(sellerUserId)).isEqualTo(1_200L);
    }

    @Test
    void shipPhysicalOrderShouldPersistShipmentAndSetSevenDayAutoConfirm() {
        UUID sellerUserId = uuid(7);
        UUID buyerUserId = uuid(9);
        seedBuyerBalance(buyerUserId, 20_000L);
        UUID orderId = seedEscrowedPhysicalOrder(sellerUserId, buyerUserId);

        MarketOrderResult shipped =
                marketOrderService.shipPhysicalOrder(orderId, sellerUserId, "顺丰", "SF1234567890", "工作日派送");

        assertThat(shipped.status()).isEqualTo("SHIPPED");
        assertThat(shipped.autoConfirmAt()).isNotNull();
        assertThat(marketQueryService.getOrderDetail(orderId, buyerUserId).shipment().trackingNo()).isEqualTo("SF1234567890");
    }

    @Test
    void cancelPhysicalOrderBeforeShipmentShouldRefundBuyer() {
        UUID sellerUserId = uuid(7);
        UUID buyerUserId = uuid(9);
        seedBuyerBalance(buyerUserId, 20_000L);
        UUID orderId = seedEscrowedPhysicalOrder(sellerUserId, buyerUserId);

        MarketOrderResult cancelled = marketOrderService.cancelOrder(orderId, buyerUserId);

        assertThat(cancelled.status()).isEqualTo("REFUND_PENDING");
        marketWalletActionProcessor.processDue(10);

        assertThat(marketQueryService.getOrderDetail(orderId, buyerUserId).status()).isEqualTo("CANCELLED");
        assertThat(balanceOfUser(buyerUserId)).isEqualTo(20_000L);
    }

    @Test
    void cancelEscrowPendingOrderShouldPreventLaterEscrowWalletCall() {
        UUID sellerUserId = uuid(7);
        UUID buyerUserId = uuid(9);
        UUID listingId = seedPhysicalListing(sellerUserId);
        UUID addressId = seedAddress(buyerUserId, true);
        seedBuyerBalance(buyerUserId, 20_000L);
        UUID orderId = marketOrderService.createOrder("cancel:pending", buyerUserId, listingId, 1, addressId).orderId();

        MarketOrderResult cancelled = marketOrderService.cancelOrder(orderId, buyerUserId);
        marketWalletActionProcessor.processDue(10);

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from wallet_txn", Integer.class)).isZero();
        assertThat(marketQueryService.getOrderDetail(orderId, buyerUserId).status()).isEqualTo("CANCELLED");
        assertThat(balanceOfUser(buyerUserId)).isEqualTo(20_000L);
    }

    @Test
    void createOrderShouldReturnExistingOrderForSameRequestIdAndPayload() {
        UUID sellerUserId = uuid(7);
        UUID buyerUserId = uuid(9);
        UUID listingId = seedPhysicalListing(sellerUserId);
        UUID addressId = seedAddress(buyerUserId, true);
        seedBuyerBalance(buyerUserId, 20_000L);

        MarketOrderResult first = marketOrderService.createOrder("physical:req-replay-ok", buyerUserId, listingId, 1, addressId);
        MarketOrderResult second = marketOrderService.createOrder("physical:req-replay-ok", buyerUserId, listingId, 1, addressId);

        assertThat(second.orderId()).isEqualTo(first.orderId());
        assertThat(second.status()).isEqualTo("ESCROW_PENDING");
        assertThat(countRows("market_order")).isEqualTo(1);
        assertThat(countRows("market_wallet_action")).isEqualTo(1);
        assertThat(countRows("wallet_txn")).isZero();
        assertThat(countRows("wallet_entry")).isZero();
    }

    @Test
    void createOrderShouldAllowSameRequestIdForDifferentBuyers() {
        UUID sellerUserId = uuid(7);
        UUID firstBuyerUserId = uuid(9);
        UUID secondBuyerUserId = uuid(10);
        UUID listingId = seedManualVirtualListing(sellerUserId, "邀请码", 1_200L);
        seedBuyerBalance(firstBuyerUserId, 20_000L);
        seedBuyerBalance(secondBuyerUserId, 20_000L);

        MarketOrderResult first = marketOrderService.createOrder("virtual:req-shared-by-buyers", firstBuyerUserId, listingId, 1, null);
        MarketOrderResult second = marketOrderService.createOrder("virtual:req-shared-by-buyers", secondBuyerUserId, listingId, 1, null);

        assertThat(second.orderId()).isNotEqualTo(first.orderId());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from market_order where request_id = ?",
                Integer.class,
                "virtual:req-shared-by-buyers"
        )).isEqualTo(2);
    }

    @Test
    void createOrderShouldRejectReplayWhenListingDoesNotMatchExistingOrder() {
        UUID sellerUserId = uuid(7);
        UUID buyerUserId = uuid(9);
        UUID firstListingId = seedManualVirtualListing(sellerUserId, "邀请码 A", 1_200L);
        UUID secondListingId = seedManualVirtualListing(sellerUserId, "邀请码 B", 1_500L);
        seedBuyerBalance(buyerUserId, 20_000L);

        marketOrderService.createOrder("virtual:req-listing-mismatch", buyerUserId, firstListingId, 1, null);

        assertThatThrownBy(() -> marketOrderService.createOrder("virtual:req-listing-mismatch", buyerUserId, secondListingId, 1, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode().getKind()).isEqualTo(ErrorKind.CONFLICT))
                .hasMessageContaining("requestId");
    }

    @Test
    void createOrderShouldRejectReplayWhenPhysicalAddressDoesNotMatchExistingOrder() {
        UUID sellerUserId = uuid(7);
        UUID buyerUserId = uuid(9);
        UUID listingId = seedPhysicalListing(sellerUserId);
        UUID firstAddressId = seedAddress(buyerUserId, true);
        UUID secondAddressId = seedAddress(buyerUserId, false);
        seedBuyerBalance(buyerUserId, 20_000L);

        marketOrderService.createOrder("physical:req-address-mismatch", buyerUserId, listingId, 1, firstAddressId);

        assertThatThrownBy(() -> marketOrderService.createOrder("physical:req-address-mismatch", buyerUserId, listingId, 1, secondAddressId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode().getKind()).isEqualTo(ErrorKind.CONFLICT))
                .hasMessageContaining("requestId");
    }

    @Test
    void createOrderShouldRejectTotalAmountOverflowBeforeOrderAndStockWrites() {
        UUID sellerUserId = uuid(7);
        UUID buyerUserId = uuid(9);
        UUID listingId = seedPhysicalListing(sellerUserId, 2, Long.MAX_VALUE, 2);
        UUID addressId = seedAddress(buyerUserId, true);
        seedBuyerBalance(buyerUserId, Long.MAX_VALUE);

        assertThatThrownBy(() -> marketOrderService.createOrder("physical:req-overflow", buyerUserId, listingId, 2, addressId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("total amount");

        assertThat(countRows("market_order")).isZero();
        assertThat(countRows("market_wallet_action")).isZero();
        assertThat(stockAvailable(listingId)).isEqualTo(2);
    }

    @Test
    void createOrderShouldRejectTotalAmountAboveMaximumBeforeOrderAndStockWrites() {
        UUID sellerUserId = uuid(7);
        UUID buyerUserId = uuid(9);
        UUID listingId = seedPhysicalListing(sellerUserId, 2, 100_000_001L, 1);
        UUID addressId = seedAddress(buyerUserId, true);
        seedBuyerBalance(buyerUserId, 200_000_000L);

        assertThatThrownBy(() -> marketOrderService.createOrder("physical:req-too-large", buyerUserId, listingId, 1, addressId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("total amount");

        assertThat(countRows("market_order")).isZero();
        assertThat(countRows("market_wallet_action")).isZero();
        assertThat(stockAvailable(listingId)).isEqualTo(2);
    }

    @Test
    void concurrentReplayShouldReturnCommittedOrderEvenIfListingTurnsSoldOutBeforeRetryContinues() throws Exception {
        UUID sellerUserId = uuid(7);
        UUID buyerUserId = uuid(9);
        UUID listingId = seedPhysicalListing(sellerUserId, 1);
        UUID addressId = seedAddress(buyerUserId, true);
        seedBuyerBalance(buyerUserId, 20_000L);

        String requestId = "physical:req-concurrent-replay";
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Connection lockConnection = dataSource.getConnection();
             PreparedStatement lockStatement = lockConnection.prepareStatement(
                     "select listing_id from market_listing where listing_id = ? for update"
             )) {
            lockConnection.setAutoCommit(false);
            lockStatement.setBytes(1, BinaryUuidCodec.toBytes(listingId));
            lockStatement.executeQuery();

            Future<MarketOrderResult> firstAttempt =
                    executor.submit(() -> marketOrderService.createOrder(requestId, buyerUserId, listingId, 1, addressId));
            TimeUnit.MILLISECONDS.sleep(200);
            assertThat(firstAttempt.isDone()).isFalse();

            Future<MarketOrderResult> replayAttempt =
                    executor.submit(() -> marketOrderService.createOrder(requestId, buyerUserId, listingId, 1, addressId));
            TimeUnit.MILLISECONDS.sleep(200);
            assertThat(replayAttempt.isDone()).isFalse();

            lockConnection.commit();
            MarketOrderResult firstOrder = firstAttempt.get(5, TimeUnit.SECONDS);
            MarketOrderResult replayOrder = replayAttempt.get(5, TimeUnit.SECONDS);

            assertThat(replayOrder.orderId()).isEqualTo(firstOrder.orderId());
            assertThat(replayOrder.status()).isEqualTo(firstOrder.status());
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from market_order where request_id = ?",
                    Integer.class,
                    requestId
            )).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private UUID seedDeliveredVirtualOrder(UUID sellerUserId, UUID buyerUserId) {
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

        var inventory = new com.nowcoder.community.market.controller.dto.AddMarketInventoryBatchRequest();
        inventory.setPayloadType("CODE");
        inventory.setPayloads(List.of("CODE-001"));

        UUID listingId = marketListingService.createListing(MarketTestCommands.listingCommand(sellerUserId, request, inventory)).listingId();
        MarketOrderResult created = marketOrderService.createOrder("virtual:req-1", buyerUserId, listingId, 1, null);
        marketWalletActionProcessor.processDue(10);
        return created.orderId();
    }

    private UUID seedEscrowedVirtualOrder(UUID sellerUserId, UUID buyerUserId) {
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

        UUID listingId = marketListingService.createListing(MarketTestCommands.listingCommand(sellerUserId, request, null)).listingId();
        MarketOrderResult created = marketOrderService.createOrder("virtual:manual:req-1", buyerUserId, listingId, 1, null);
        marketWalletActionProcessor.processDue(10);
        return created.orderId();
    }

    private UUID seedManualVirtualListing(UUID sellerUserId, String title, long unitPrice) {
        CreateMarketListingRequest request = new CreateMarketListingRequest();
        request.setGoodsType("VIRTUAL");
        request.setTitle(title);
        request.setDescription("手工交付");
        request.setUnitPrice(unitPrice);
        request.setDeliveryMode("MANUAL");
        request.setStockMode("FINITE");
        request.setStockTotal(2);
        request.setMinPurchaseQuantity(1);
        request.setMaxPurchaseQuantity(2);
        return marketListingService.createListing(MarketTestCommands.listingCommand(sellerUserId, request, null)).listingId();
    }

    private UUID seedEscrowedPhysicalOrder(UUID sellerUserId, UUID buyerUserId) {
        UUID listingId = seedPhysicalListing(sellerUserId);
        UUID addressId = seedAddress(buyerUserId, true);
        MarketOrderResult created = marketOrderService.createOrder("physical:req-2", buyerUserId, listingId, 1, addressId);
        marketWalletActionProcessor.processDue(10);
        return created.orderId();
    }

    private UUID seedPhysicalListing(UUID sellerUserId) {
        return seedPhysicalListing(sellerUserId, 3);
    }

    private UUID seedPhysicalListing(UUID sellerUserId, int stockTotal) {
        return seedPhysicalListing(sellerUserId, stockTotal, 12_900L, 1);
    }

    private UUID seedPhysicalListing(UUID sellerUserId, int stockTotal, long unitPrice, int maxPurchaseQuantity) {
        CreateMarketListingRequest request = new CreateMarketListingRequest();
        request.setGoodsType("PHYSICAL");
        request.setTitle("二手键盘");
        request.setDescription("九成新");
        request.setUnitPrice(unitPrice);
        request.setStockTotal(stockTotal);
        request.setMinPurchaseQuantity(1);
        request.setMaxPurchaseQuantity(maxPurchaseQuantity);
        return marketListingService.createListing(MarketTestCommands.listingCommand(sellerUserId, request, null)).listingId();
    }

    private UUID seedAddress(UUID userId, boolean isDefault) {
        CreateMarketAddressRequest request = new CreateMarketAddressRequest();
        request.setReceiverName("张三");
        request.setReceiverPhone("13800000000");
        request.setProvince("上海市");
        request.setCity("上海市");
        request.setDistrict("浦东新区");
        request.setDetailAddress("世纪大道 100 号");
        request.setPostalCode("200120");
        request.setDefaultAddress(isDefault);
        return marketAddressService.createAddress(MarketTestCommands.addressCommand(userId, request)).addressId();
    }

    private void seedBuyerBalance(UUID userId, long balance) {
        UUID accountId = walletAccountService.ensureUserWallet(userId);
        jdbcTemplate.update(
                "update wallet_account set balance = ?, version = 0, status = 'ACTIVE' where account_id = ?",
                balance,
                accountId
        );
    }

    private long balanceOfUser(UUID userId) {
        return walletAccountService.balanceOfUser(userId);
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    private int stockAvailable(UUID listingId) {
        Integer stockAvailable = jdbcTemplate.queryForObject(
                "select stock_available from market_listing where listing_id = ?",
                Integer.class,
                listingId
        );
        return stockAvailable == null ? 0 : stockAvailable;
    }
}
