package com.nowcoder.community.market.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.domain.model.MarketListing;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.model.MarketOrderStatus;
import com.nowcoder.community.market.domain.model.MarketOrderTransition;
import com.nowcoder.community.market.domain.repository.MarketInventoryRepository;
import com.nowcoder.community.market.domain.repository.MarketListingRepository;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketListingDataObject;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketOrderDataObject;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketInventoryUnitMapper;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketListingMapper;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static com.nowcoder.community.market.support.MarketOrderTestFixture.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class MarketOrderSagaApplicationServiceTest {

    private final UUID sellerUserId = uuid(7);
    private final UUID buyerUserId = uuid(9);
    private final UUID escrowTxnId = uuid(201);
    private final UUID refundTxnId = uuid(202);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketOrderSagaApplicationService sagaService;

    @Autowired
    private MarketListingMapper marketListingMapper;

    @Autowired
    private MarketOrderMapper marketOrderMapper;

    @Autowired
    private MarketOrderRepository marketOrderRepository;

    @Autowired
    private MarketInventoryUnitMapper marketInventoryUnitMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    private UUID listingId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from market_wallet_action");
        jdbcTemplate.update("delete from market_dispute");
        jdbcTemplate.update("delete from market_order");
        jdbcTemplate.update("delete from market_inventory_unit");
        jdbcTemplate.update("delete from market_listing");
    }

    @Test
    void markEscrowSucceededShouldAdvanceOnlyFromEscrowPending() {
        seedOrder("ESCROW_PENDING");

        boolean advanced = sagaService.markEscrowSucceeded(orderId, escrowTxnId);

        assertThat(advanced).isTrue();
        assertThat(storedOrder(orderId).getStatus()).isEqualTo("ESCROWED");
        assertThat(storedOrder(orderId).getEscrowTxnId()).isEqualTo(escrowTxnId);
    }

    @Test
    void markEscrowSucceededShouldNotOverwriteCancelledOrder() {
        seedOrder("ESCROW_CANCEL_PENDING");

        boolean advanced = sagaService.markEscrowSucceeded(orderId, escrowTxnId);

        assertThat(advanced).isFalse();
        assertThat(storedOrder(orderId).getEscrowTxnId()).isNull();
        assertThat(storedOrder(orderId).getStatus()).isEqualTo("ESCROW_CANCEL_PENDING");
    }

    @Test
    void completeEscrowNoopShouldCancelEscrowFailedWithoutCompensatingInventoryAgain() {
        MarketOrderRepository orderRepository = mock(MarketOrderRepository.class);
        MarketListingRepository listingRepository = mock(MarketListingRepository.class);
        MarketInventoryRepository inventoryRepository = mock(MarketInventoryRepository.class);
        UUID failedOrderId = uuid(301);
        UUID failedListingId = uuid(302);
        MarketOrder failedOrder = order(failedOrderId)
                .listingId(failedListingId)
                .goodsType("VIRTUAL")
                .deliveryModeSnapshot("PRELOADED")
                .status("ESCROW_FAILED")
                .build();
        MarketListing listing = finiteListing(failedListingId, 1);
        when(orderRepository.lockById(failedOrderId)).thenReturn(failedOrder);
        when(orderRepository.apply(any())).thenReturn(MarketOrderRepository.ApplyStatus.APPLIED);
        when(listingRepository.lockById(failedListingId)).thenReturn(listing);
        MarketOrderSagaApplicationService service = new MarketOrderSagaApplicationService(
                orderRepository,
                listingRepository,
                inventoryRepository
        );

        service.completeEscrowNoop(failedOrderId);

        ArgumentCaptor<MarketOrderTransition> transitionCaptor = ArgumentCaptor.forClass(MarketOrderTransition.class);
        verify(orderRepository).apply(transitionCaptor.capture());
        assertThat(transitionCaptor.getValue().nextStatus()).isEqualTo(MarketOrderStatus.CANCELLED);
        verify(listingRepository, never()).adjustStock(any(), any(), anyInt(), anyInt(), any());
        verify(inventoryRepository, never()).releaseReservedByOrderIfNeeded(any());
    }

    @Test
    void completeEscrowNoopShouldCompensateEscrowCancelPendingInventoryOnce() {
        MarketOrderRepository orderRepository = mock(MarketOrderRepository.class);
        MarketListingRepository listingRepository = mock(MarketListingRepository.class);
        MarketInventoryRepository inventoryRepository = mock(MarketInventoryRepository.class);
        UUID cancelledOrderId = uuid(311);
        UUID cancelledListingId = uuid(312);
        MarketOrder cancelPendingOrder = order(cancelledOrderId)
                .listingId(cancelledListingId)
                .goodsType("VIRTUAL")
                .deliveryModeSnapshot("PRELOADED")
                .status("ESCROW_CANCEL_PENDING")
                .build();
        MarketListing listing = finiteListing(cancelledListingId, 0);
        when(orderRepository.lockById(cancelledOrderId)).thenReturn(cancelPendingOrder);
        when(orderRepository.apply(any())).thenReturn(MarketOrderRepository.ApplyStatus.APPLIED);
        when(listingRepository.lockById(cancelledListingId)).thenReturn(listing);
        MarketOrderSagaApplicationService service = new MarketOrderSagaApplicationService(
                orderRepository,
                listingRepository,
                inventoryRepository
        );

        service.completeEscrowNoop(cancelledOrderId);

        verify(listingRepository, times(1)).adjustStock(
                cancelledListingId,
                sellerUserId,
                0,
                1,
                "ACTIVE"
        );
        verify(inventoryRepository, times(1)).releaseReservedByOrderIfNeeded(cancelledOrderId);
    }

    @Test
    void completeEscrowNoopShouldNotCompensateWhenCancelTransitionIsStale() {
        MarketOrderRepository orderRepository = mock(MarketOrderRepository.class);
        MarketListingRepository listingRepository = mock(MarketListingRepository.class);
        MarketInventoryRepository inventoryRepository = mock(MarketInventoryRepository.class);
        UUID staleOrderId = uuid(321);
        MarketOrder cancelPendingOrder = order(staleOrderId)
                .goodsType("VIRTUAL")
                .deliveryModeSnapshot("PRELOADED")
                .status("ESCROW_CANCEL_PENDING")
                .build();
        when(orderRepository.lockById(staleOrderId)).thenReturn(cancelPendingOrder);
        when(orderRepository.apply(any())).thenReturn(MarketOrderRepository.ApplyStatus.STALE);
        MarketOrderSagaApplicationService service = new MarketOrderSagaApplicationService(
                orderRepository,
                listingRepository,
                inventoryRepository
        );

        service.completeEscrowNoop(staleOrderId);

        verify(listingRepository, never()).adjustStock(any(), any(), anyInt(), anyInt(), any());
        verify(inventoryRepository, never()).releaseReservedByOrderIfNeeded(any());
    }

    @Test
    void markRefundSucceededShouldRestoreInventoryOnce() {
        seedFiniteStockOrder("REFUND_PENDING", 0);

        assertThat(sagaService.markRefundSucceeded(orderId, refundTxnId)).isTrue();
        sagaService.markRefundSucceeded(orderId, refundTxnId);

        Integer stock = jdbcTemplate.queryForObject(
                "select stock_available from market_listing where listing_id = ?",
                Integer.class,
                listingId
        );
        assertThat(stock).isEqualTo(1);
        assertThat(storedOrder(orderId).getStatus()).isEqualTo("CANCELLED");
        assertThat(storedOrder(orderId).getRefundTxnId()).isEqualTo(refundTxnId);
    }

    @Test
    void markRefundSucceededShouldNotRestoreStockForDisputeRefundedPhysicalOrder() {
        seedFiniteStockOrder("DISPUTE_REFUND_PENDING", 0);

        assertThat(sagaService.markRefundSucceeded(orderId, refundTxnId)).isTrue();

        Integer stock = jdbcTemplate.queryForObject(
                "select stock_available from market_listing where listing_id = ?",
                Integer.class,
                listingId
        );
        assertThat(stock).isEqualTo(0);
        assertThat(storedOrder(orderId).getStatus()).isEqualTo("REFUNDED");
    }

    @Test
    void markRefundSucceededShouldNotRestorePreloadedDeliveredInventoryAsAvailableStock() {
        seedPreloadedDeliveredDisputeRefundOrder();

        assertThat(sagaService.markRefundSucceeded(orderId, refundTxnId)).isTrue();

        Integer stock = jdbcTemplate.queryForObject(
                "select stock_available from market_listing where listing_id = ?",
                Integer.class,
                listingId
        );
        String inventoryStatus = jdbcTemplate.queryForObject(
                "select status from market_inventory_unit where reserved_order_id = ?",
                String.class,
                orderId
        );
        assertThat(stock).isEqualTo(0);
        assertThat(inventoryStatus).isEqualTo("DELIVERED");
        assertThat(storedOrder(orderId).getStatus()).isEqualTo("REFUNDED");
    }

    @Test
    void markDeliveredShouldRequireEscrowedStatusInPersistence() {
        seedFiniteStockOrder("REFUND_PENDING", 0);

        MarketOrder stale = order(storedOrder(orderId)).status("ESCROWED").build();
        MarketOrderRepository.ApplyStatus outcome = marketOrderRepository.apply(
                stale.markDelivered(new java.util.Date())
        );

        assertThat(outcome).isEqualTo(MarketOrderRepository.ApplyStatus.STALE);
        assertThat(storedOrder(orderId).getStatus()).isEqualTo("REFUND_PENDING");
    }

    @Test
    void markShippedShouldRequireEscrowedStatusInPersistence() {
        seedFiniteStockOrder("REFUND_PENDING", 0);

        MarketOrder stale = order(storedOrder(orderId)).status("ESCROWED").build();
        MarketOrderRepository.ApplyStatus outcome = marketOrderRepository.apply(
                stale.markShipped(new java.util.Date())
        );

        assertThat(outcome).isEqualTo(MarketOrderRepository.ApplyStatus.STALE);
        assertThat(storedOrder(orderId).getStatus()).isEqualTo("REFUND_PENDING");
    }

    @Test
    void markDisputedShouldRequireDeliveredOrShippedStatusInPersistence() {
        seedFiniteStockOrder("RELEASE_PENDING", 0);

        MarketOrder stale = order(storedOrder(orderId)).status("DELIVERED").build();
        MarketOrderRepository.ApplyStatus outcome = marketOrderRepository.apply(stale.openDispute());

        assertThat(outcome).isEqualTo(MarketOrderRepository.ApplyStatus.STALE);
        assertThat(storedOrder(orderId).getStatus()).isEqualTo("RELEASE_PENDING");
    }

    private MarketOrder storedOrder(UUID id) {
        return marketOrderMapper.selectById(id).toDomain();
    }

    private void seedOrder(String status) {
        seedFiniteStockOrder(status, 3);
    }

    private MarketListing finiteListing(UUID id, int stockAvailable) {
        MarketListing listing = new MarketListing();
        listing.setListingId(id);
        listing.setSellerUserId(sellerUserId);
        listing.setGoodsType("VIRTUAL");
        listing.setStockMode("FINITE");
        listing.setStockAvailable(stockAvailable);
        listing.setStatus(stockAvailable == 0 ? "SOLD_OUT" : "ACTIVE");
        return listing;
    }

    private void seedFiniteStockOrder(String status, int stockAvailable) {
        listingId = uuid(Math.abs(status.hashCode() % 10_000) + 1);
        orderId = uuid(Math.abs((status + ":order").hashCode() % 10_000) + 1);

        MarketListing listing = new MarketListing();
        listing.setListingId(listingId);
        listing.setSellerUserId(sellerUserId);
        listing.setGoodsType("PHYSICAL");
        listing.setTitle("二手键盘");
        listing.setDescription("九成新");
        listing.setUnitPrice(12_900L);
        listing.setStockMode("FINITE");
        listing.setStockTotal(3);
        listing.setStockAvailable(stockAvailable);
        listing.setMinPurchaseQuantity(1);
        listing.setMaxPurchaseQuantity(1);
        listing.setStatus(stockAvailable == 0 ? "SOLD_OUT" : "ACTIVE");
        marketListingMapper.insert(MarketListingDataObject.from(listing));

        MarketOrder seededOrder = order(orderId)
                .requestId("saga:" + status.toLowerCase())
                .listingId(listingId)
                .goodsType("PHYSICAL")
                .sellerUserId(sellerUserId)
                .buyerUserId(buyerUserId)
                .quantity(1)
                .unitPriceSnapshot(12_900L)
                .totalAmount(12_900L)
                .deliveryModeSnapshot("MANUAL")
                .listingTitleSnapshot("二手键盘")
                .status(status)
                .build();
        marketOrderMapper.insert(MarketOrderDataObject.from(seededOrder));
    }

    private void seedPreloadedDeliveredDisputeRefundOrder() {
        listingId = uuid(6001);
        orderId = uuid(6002);
        UUID inventoryUnitId = uuid(6003);

        MarketListing listing = new MarketListing();
        listing.setListingId(listingId);
        listing.setSellerUserId(sellerUserId);
        listing.setGoodsType("VIRTUAL");
        listing.setTitle("序列号");
        listing.setDescription("自动交付");
        listing.setUnitPrice(12_900L);
        listing.setDeliveryMode("PRELOADED");
        listing.setStockMode("FINITE");
        listing.setStockTotal(1);
        listing.setStockAvailable(0);
        listing.setMinPurchaseQuantity(1);
        listing.setMaxPurchaseQuantity(1);
        listing.setStatus("SOLD_OUT");
        marketListingMapper.insert(MarketListingDataObject.from(listing));

        jdbcTemplate.update(
                "insert into market_inventory_unit(inventory_unit_id, listing_id, seller_user_id, payload_type, payload_content, status, reserved_order_id, delivered_at) values (?, ?, ?, ?, ?, ?, ?, current_timestamp)",
                inventoryUnitId,
                listingId,
                sellerUserId,
                "TEXT",
                "CODE-001",
                "DELIVERED",
                orderId
        );

        MarketOrder seededOrder = order(orderId)
                .requestId("saga:preloaded-dispute-refund")
                .listingId(listingId)
                .goodsType("VIRTUAL")
                .sellerUserId(sellerUserId)
                .buyerUserId(buyerUserId)
                .quantity(1)
                .unitPriceSnapshot(12_900L)
                .totalAmount(12_900L)
                .deliveryModeSnapshot("PRELOADED")
                .listingTitleSnapshot("序列号")
                .status("DISPUTE_REFUND_PENDING")
                .build();
        marketOrderMapper.insert(MarketOrderDataObject.from(seededOrder));
    }
}
