package com.nowcoder.community.market.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.application.result.MarketWalletActionRecoveryResult;
import com.nowcoder.community.market.domain.model.MarketListing;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.model.MarketWalletAction;
import com.nowcoder.community.market.domain.model.MarketWalletActionLease;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import com.nowcoder.community.market.domain.repository.MarketWalletActionRepository;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketListingDataObject;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketOrderDataObject;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketWalletActionDataObject;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketListingMapper;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketOrderMapper;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketWalletActionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static com.nowcoder.community.market.support.MarketOrderTestFixture.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class MarketWalletActionRecoveryApplicationServiceTest {

    private final UUID sellerUserId = uuid(7);
    private final UUID buyerUserId = uuid(9);
    private final UUID refundTxnId = uuid(202);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketWalletActionRecoveryApplicationService recoveryService;

    @Autowired
    private MarketListingMapper marketListingMapper;

    @Autowired
    private MarketOrderMapper marketOrderMapper;

    @Autowired
    private MarketWalletActionMapper marketWalletActionMapper;

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
    void recoverExpiredProcessingShouldReturnActionToRetrying() {
        UUID actionId = seedProcessingActionWithExpiredLease(Instant.parse("2026-04-25T09:00:00Z"));

        int recovered = recoveryService.recoverExpiredProcessing(Instant.parse("2026-04-25T10:00:00Z"));

        assertThat(recovered).isEqualTo(1);
        MarketWalletAction action = marketWalletActionMapper.selectById(actionId);
        assertThat(action.getStatus()).isEqualTo("RETRYING");
        assertThat(action.getProcessingLeaseUntil()).isNull();
        assertThat(action.getLeaseToken()).isNull();
    }

    @Test
    void reconcileWalletTxnWithoutSucceededActionShouldAdvanceRemainingMarketState() {
        seedRefundPendingOrder();
        seedRefundActionWithWalletTxn();

        MarketWalletActionRecoveryResult result = recoveryService.reconcileOnce(50);

        assertThat(result.reconciledCount()).isEqualTo(1);
        assertThat(marketOrderMapper.selectById(orderId).getStatus()).isEqualTo("CANCELLED");
        assertThat(marketWalletActionMapper.selectByOrderAndType(orderId, "REFUND").getStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    void reconcileReleasePendingOrderShouldRepairFailedReleaseActionForRetry() {
        seedReleasePendingOrder();
        seedFailedActionWithoutWalletTxn("RELEASE");

        MarketWalletActionRecoveryResult result = recoveryService.reconcileOnce(50);

        assertThat(result.reconciledCount()).isEqualTo(1);
        assertThat(marketOrderMapper.selectById(orderId).getStatus()).isEqualTo("RELEASE_PENDING");
        MarketWalletAction action = marketWalletActionMapper.selectByOrderAndType(orderId, "RELEASE");
        assertThat(action.getStatus()).isEqualTo("RETRYING");
        assertThat(action.getWalletTxnId()).isNull();
        assertThat(action.getNextRetryAt()).isNotNull();
    }

    @Test
    void reconcileRefundPendingOrderShouldRepairFailedRefundActionForRetry() {
        seedRefundPendingOrder();
        seedFailedActionWithoutWalletTxn("REFUND");

        MarketWalletActionRecoveryResult result = recoveryService.reconcileOnce(50);

        assertThat(result.reconciledCount()).isEqualTo(1);
        assertThat(marketOrderMapper.selectById(orderId).getStatus()).isEqualTo("REFUND_PENDING");
        MarketWalletAction action = marketWalletActionMapper.selectByOrderAndType(orderId, "REFUND");
        assertThat(action.getStatus()).isEqualTo("RETRYING");
        assertThat(action.getWalletTxnId()).isNull();
        assertThat(action.getNextRetryAt()).isNotNull();
    }

    @Test
    void reconcileRefundPendingOrderShouldLeavePermanentFailedRefundVisible() {
        seedRefundPendingOrder();
        seedFailedActionWithoutWalletTxn("REFUND", "17001");

        MarketWalletActionRecoveryResult result = recoveryService.reconcileOnce(50);

        assertThat(result.reconciledCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(marketOrderMapper.selectById(orderId).getStatus()).isEqualTo("REFUND_PENDING");
        MarketWalletAction action = marketWalletActionMapper.selectByOrderAndType(orderId, "REFUND");
        assertThat(action.getStatus()).isEqualTo("FAILED");
        assertThat(action.getFailureCode()).isEqualTo("17001");
        assertThat(action.getNextRetryAt()).isNull();
    }

    @Test
    void reconcileOnceShouldNotStarvePendingOrdersWhenEarlierWalletTxnActionsAreSkipped() {
        seedSkippedWalletTxnAction();
        seedRefundPendingOrder();

        MarketWalletActionRecoveryResult result = recoveryService.reconcileOnce(1);

        assertThat(result.reconciledCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(marketWalletActionMapper.selectByOrderAndType(orderId, "REFUND")).isNotNull();
    }

    @Test
    void reconcileWalletTxnShouldCountSkippedWhenExpectedFactCasMisses() {
        MarketWalletActionRepository walletActionRepository = mock(MarketWalletActionRepository.class);
        MarketOrderRepository orderRepository = mock(MarketOrderRepository.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        MarketWalletAction action = action(uuid(601), uuid(602), "RELEASE");
        action.setStatus("FAILED");
        action.setWalletTxnId(uuid(603));
        when(walletActionRepository.findUnfinishedWithWalletTxn(1)).thenReturn(List.of(action));
        when(orderRepository.findWalletPendingOrders(1)).thenReturn(List.of());
        when(sagaService.markReleaseSucceeded(action.getOrderId(), action.getWalletTxnId())).thenReturn(true);
        when(walletActionRepository.markRecoveredSucceeded(
                action.getActionId(),
                action.getStatus(),
                action.getWalletTxnId(),
                "APPLIED"
        )).thenReturn(0);
        MarketWalletActionRecoveryApplicationService service = new MarketWalletActionRecoveryApplicationService(
                walletActionRepository,
                orderRepository,
                sagaService,
                actionService,
                Clock.fixed(Instant.parse("2026-04-25T10:00:00Z"), ZoneOffset.UTC)
        );

        MarketWalletActionRecoveryResult result = service.reconcileOnce(1);

        assertThat(result.reconciledCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(1);
        verify(walletActionRepository).markRecoveredSucceeded(
                action.getActionId(),
                "FAILED",
                action.getWalletTxnId(),
                "APPLIED"
        );
        verify(walletActionRepository, never()).markSucceeded(
                any(MarketWalletActionLease.class),
                any(),
                any()
        );
    }

    @Test
    void reconcileWalletTxnShouldCountReconciledWhenExpectedFactCasMatches() {
        MarketWalletActionRepository walletActionRepository = mock(MarketWalletActionRepository.class);
        MarketOrderRepository orderRepository = mock(MarketOrderRepository.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        MarketWalletAction action = action(uuid(604), uuid(605), "REFUND");
        action.setStatus("FAILED");
        action.setWalletTxnId(uuid(606));
        when(walletActionRepository.findUnfinishedWithWalletTxn(1)).thenReturn(List.of(action));
        when(sagaService.markRefundSucceeded(action.getOrderId(), action.getWalletTxnId())).thenReturn(true);
        when(walletActionRepository.markRecoveredSucceeded(
                action.getActionId(),
                action.getStatus(),
                action.getWalletTxnId(),
                "APPLIED"
        )).thenReturn(1);
        MarketWalletActionRecoveryApplicationService service = new MarketWalletActionRecoveryApplicationService(
                walletActionRepository,
                orderRepository,
                sagaService,
                actionService,
                Clock.fixed(Instant.parse("2026-04-25T10:00:00Z"), ZoneOffset.UTC)
        );

        MarketWalletActionRecoveryResult result = service.reconcileOnce(1);

        assertThat(result.reconciledCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();
        verify(walletActionRepository).markRecoveredSucceeded(
                action.getActionId(),
                "FAILED",
                action.getWalletTxnId(),
                "APPLIED"
        );
        verify(walletActionRepository, never()).markSucceeded(
                any(MarketWalletActionLease.class),
                any(),
                any()
        );
    }

    @Test
    void reconcileWalletTxnShouldNotTakeOwnershipFromProcessingAction() {
        MarketWalletActionRepository walletActionRepository = mock(MarketWalletActionRepository.class);
        MarketOrderRepository orderRepository = mock(MarketOrderRepository.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        MarketWalletAction action = action(uuid(607), uuid(608), "RELEASE");
        action.setStatus("PROCESSING");
        action.setWalletTxnId(uuid(609));
        action.setLeaseToken(uuid(610));
        when(walletActionRepository.findUnfinishedWithWalletTxn(1)).thenReturn(List.of(action));
        when(orderRepository.findWalletPendingOrders(1)).thenReturn(List.of());
        MarketWalletActionRecoveryApplicationService service = new MarketWalletActionRecoveryApplicationService(
                walletActionRepository,
                orderRepository,
                sagaService,
                actionService,
                Clock.fixed(Instant.parse("2026-04-25T10:00:00Z"), ZoneOffset.UTC)
        );

        MarketWalletActionRecoveryResult result = service.reconcileOnce(1);

        assertThat(result.reconciledCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(1);
        verify(sagaService, never()).markReleaseSucceeded(any(), any());
        verify(walletActionRepository, never()).markRecoveredSucceeded(any(), any(), any(), any());
    }

    @Test
    void reconcileFailedActionShouldUseExpectedFailureCas() {
        MarketWalletActionRepository walletActionRepository = mock(MarketWalletActionRepository.class);
        MarketOrderRepository orderRepository = mock(MarketOrderRepository.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        Instant now = Instant.parse("2026-04-25T10:00:00Z");
        UUID actionOrderId = uuid(611);
        MarketOrder pendingOrder = order(actionOrderId).status("RELEASE_PENDING").build();
        MarketWalletAction action = action(uuid(612), actionOrderId, "RELEASE");
        action.setStatus("FAILED");
        action.setFailureCode("17004");
        action.setLastError("wallet conflict");
        when(walletActionRepository.findUnfinishedWithWalletTxn(1)).thenReturn(List.of());
        when(orderRepository.findWalletPendingOrders(1)).thenReturn(List.of(pendingOrder));
        when(walletActionRepository.findByOrderAndType(actionOrderId, "RELEASE")).thenReturn(action);
        when(walletActionRepository.rescheduleFailed(
                action.getActionId(),
                action.getFailureCode(),
                Date.from(now),
                action.getLastError()
        )).thenReturn(1);
        MarketWalletActionRecoveryApplicationService service = new MarketWalletActionRecoveryApplicationService(
                walletActionRepository,
                orderRepository,
                sagaService,
                actionService,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        MarketWalletActionRecoveryResult result = service.reconcileOnce(1);

        assertThat(result.reconciledCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();
        verify(walletActionRepository).rescheduleFailed(
                action.getActionId(),
                "17004",
                Date.from(now),
                "wallet conflict"
        );
        verify(walletActionRepository, never()).markRetrying(
                any(MarketWalletActionLease.class),
                any(),
                any()
        );
    }

    @Test
    void reconcileFailedActionShouldCountSkippedWhenExpectedFailureCasMisses() {
        MarketWalletActionRepository walletActionRepository = mock(MarketWalletActionRepository.class);
        MarketOrderRepository orderRepository = mock(MarketOrderRepository.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        Instant now = Instant.parse("2026-04-25T10:00:00Z");
        UUID actionOrderId = uuid(613);
        MarketOrder pendingOrder = order(actionOrderId).status("REFUND_PENDING").build();
        MarketWalletAction action = action(uuid(614), actionOrderId, "REFUND");
        action.setStatus("FAILED");
        action.setFailureCode("17004");
        action.setLastError("wallet conflict");
        when(walletActionRepository.findUnfinishedWithWalletTxn(1)).thenReturn(List.of());
        when(orderRepository.findWalletPendingOrders(1)).thenReturn(List.of(pendingOrder));
        when(walletActionRepository.findByOrderAndType(actionOrderId, "REFUND")).thenReturn(action);
        when(walletActionRepository.rescheduleFailed(
                action.getActionId(),
                action.getFailureCode(),
                Date.from(now),
                action.getLastError()
        )).thenReturn(0);
        MarketWalletActionRecoveryApplicationService service = new MarketWalletActionRecoveryApplicationService(
                walletActionRepository,
                orderRepository,
                sagaService,
                actionService,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        MarketWalletActionRecoveryResult result = service.reconcileOnce(1);

        assertThat(result.reconciledCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(1);
        verify(walletActionRepository).rescheduleFailed(
                action.getActionId(),
                "17004",
                Date.from(now),
                "wallet conflict"
        );
        verify(walletActionRepository, never()).markRetrying(
                any(MarketWalletActionLease.class),
                any(),
                any()
        );
    }

    private UUID seedProcessingActionWithExpiredLease(Instant expiredAt) {
        UUID actionId = uuid(301);
        UUID actionOrderId = uuid(302);
        MarketWalletAction action = action(actionId, actionOrderId, "RELEASE");
        action.setStatus("PROCESSING");
        action.setProcessingLeaseUntil(Date.from(expiredAt));
        action.setLeaseToken(uuid(303));
        marketWalletActionMapper.insert(MarketWalletActionDataObject.from(action));
        return actionId;
    }

    private void seedRefundPendingOrder() {
        seedWalletPendingOrder("REFUND_PENDING");
    }

    private void seedReleasePendingOrder() {
        seedWalletPendingOrder("RELEASE_PENDING");
    }

    private void seedWalletPendingOrder(String status) {
        listingId = uuid(401);
        orderId = uuid(402);

        MarketListing listing = new MarketListing();
        listing.setListingId(listingId);
        listing.setSellerUserId(sellerUserId);
        listing.setGoodsType("PHYSICAL");
        listing.setTitle("二手键盘");
        listing.setDescription("九成新");
        listing.setUnitPrice(12_900L);
        listing.setStockMode("FINITE");
        listing.setStockTotal(1);
        listing.setStockAvailable(0);
        listing.setMinPurchaseQuantity(1);
        listing.setMaxPurchaseQuantity(1);
        listing.setStatus("SOLD_OUT");
        marketListingMapper.insert(MarketListingDataObject.from(listing));

        MarketOrder seededOrder = order(orderId)
                .requestId("recovery:refund-pending")
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

    private void seedRefundActionWithWalletTxn() {
        MarketWalletAction action = action(uuid(403), orderId, "REFUND");
        action.setStatus("FAILED");
        action.setWalletTxnId(refundTxnId);
        action.setFailureCode("SAGA_STATE_NOT_ADVANCED");
        action.setLastError("market order saga did not advance after wallet success");
        marketWalletActionMapper.insert(MarketWalletActionDataObject.from(action));
    }

    private void seedFailedActionWithoutWalletTxn(String actionType) {
        seedFailedActionWithoutWalletTxn(actionType, "17004");
    }

    private void seedFailedActionWithoutWalletTxn(String actionType, String failureCode) {
        MarketWalletAction action = action(uuid(404), orderId, actionType);
        action.setStatus("FAILED");
        action.setFailureCode(failureCode);
        action.setLastError("escrow insufficient");
        marketWalletActionMapper.insert(MarketWalletActionDataObject.from(action));
    }

    private void seedSkippedWalletTxnAction() {
        UUID skippedListingId = uuid(501);
        UUID skippedOrderId = uuid(502);
        MarketListing skippedListing = new MarketListing();
        skippedListing.setListingId(skippedListingId);
        skippedListing.setSellerUserId(sellerUserId);
        skippedListing.setGoodsType("PHYSICAL");
        skippedListing.setTitle("旧手机");
        skippedListing.setDescription("成色一般");
        skippedListing.setUnitPrice(9_900L);
        skippedListing.setStockMode("FINITE");
        skippedListing.setStockTotal(1);
        skippedListing.setStockAvailable(1);
        skippedListing.setMinPurchaseQuantity(1);
        skippedListing.setMaxPurchaseQuantity(1);
        skippedListing.setStatus("ACTIVE");
        marketListingMapper.insert(MarketListingDataObject.from(skippedListing));

        MarketOrder skippedOrder = order(skippedOrderId)
                .requestId("recovery:skipped-wallet-txn")
                .listingId(skippedListingId)
                .goodsType("PHYSICAL")
                .sellerUserId(sellerUserId)
                .buyerUserId(buyerUserId)
                .quantity(1)
                .unitPriceSnapshot(9_900L)
                .totalAmount(9_900L)
                .deliveryModeSnapshot("MANUAL")
                .listingTitleSnapshot("旧手机")
                .status("COMPLETED")
                .build();
        marketOrderMapper.insert(MarketOrderDataObject.from(skippedOrder));

        MarketWalletAction action = action(uuid(503), skippedOrderId, "REFUND");
        action.setStatus("FAILED");
        action.setWalletTxnId(uuid(504));
        action.setFailureCode("SAGA_STATE_NOT_ADVANCED");
        action.setLastError("market order saga did not advance after wallet success");
        marketWalletActionMapper.insert(MarketWalletActionDataObject.from(action));
    }

    private MarketWalletAction action(UUID actionId, UUID actionOrderId, String actionType) {
        MarketWalletAction action = new MarketWalletAction();
        action.setActionId(actionId);
        action.setOrderId(actionOrderId);
        action.setActionType(actionType);
        action.setRequestId("market-order:" + actionOrderId + ":" + actionType.toLowerCase());
        action.setWalletBizId("market-order:" + actionOrderId);
        action.setActorUserId("RELEASE".equals(actionType) ? sellerUserId : buyerUserId);
        action.setCounterpartyUserId("RELEASE".equals(actionType) ? buyerUserId : sellerUserId);
        action.setAmount(12_900L);
        action.setStatus("PENDING");
        return action;
    }
}
