package com.nowcoder.community.market.application;

import com.nowcoder.community.market.domain.model.MarketWalletAction;
import com.nowcoder.community.market.domain.model.MarketWalletActionLease;
import com.nowcoder.community.market.domain.model.MarketListing;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.model.MarketOrderTransition;
import com.nowcoder.community.market.domain.model.MarketWalletActionStatus;
import com.nowcoder.community.market.domain.repository.MarketInventoryRepository;
import com.nowcoder.community.market.domain.repository.MarketListingRepository;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import com.nowcoder.community.market.infrastructure.persistence.MyBatisMarketWalletActionRepository;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketWalletActionMapper;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.api.action.WalletMarketActionApi;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.nowcoder.community.market.support.MarketOrderTestFixture.order;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketWalletActionProcessorApplicationServiceTest {

    @Test
    void processOneShouldUseConfiguredProcessingLease() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = escrowAction();
        Instant now = Instant.parse("2026-05-18T00:00:00Z");
        when(mapper.claimProcessing(any(MarketWalletActionLease.class), any())).thenReturn(0);
        MarketWalletActionProcessorApplicationService processor = new MarketWalletActionProcessorApplicationService(
                new MyBatisMarketWalletActionRepository(mapper),
                walletApi,
                sagaService,
                actionService,
                Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofSeconds(120)
        );

        processor.processOne(action);

        ArgumentCaptor<MarketWalletActionLease> ownershipCaptor =
                ArgumentCaptor.forClass(MarketWalletActionLease.class);
        ArgumentCaptor<Date> deadlineCaptor = ArgumentCaptor.forClass(Date.class);
        verify(mapper).claimProcessing(ownershipCaptor.capture(), deadlineCaptor.capture());
        assertThat(ownershipCaptor.getValue().actionId()).isEqualTo(action.getActionId());
        assertThat(ownershipCaptor.getValue().token()).isNotNull();
        assertThat(deadlineCaptor.getValue().toInstant()).isEqualTo(now.plusSeconds(120));
    }

    @Test
    void processOneShouldNoopEscrowWhenSagaRejectsForwardAction() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = escrowAction();
        when(mapper.claimProcessing(any(MarketWalletActionLease.class), any())).thenReturn(1);
        when(mapper.markCancelled(any(MarketWalletActionLease.class), eq("NOOP"))).thenReturn(1);
        when(sagaService.canApplyEscrow(action.getOrderId())).thenReturn(false);

        MarketWalletActionProcessorApplicationService processor = new MarketWalletActionProcessorApplicationService(
                new MyBatisMarketWalletActionRepository(mapper),
                walletApi,
                sagaService,
                actionService,
                Clock.systemUTC()
        );

        boolean processed = processor.processOne(action);

        assertThat(processed).isTrue();
        verify(walletApi, never()).escrowOrder(any(), any(), anyLong(), any());
        ArgumentCaptor<MarketWalletActionLease> leaseCaptor =
                ArgumentCaptor.forClass(MarketWalletActionLease.class);
        verify(mapper).claimProcessing(leaseCaptor.capture(), any());
        verify(mapper).markCancelled(same(leaseCaptor.getValue()), eq("NOOP"));
    }

    @Test
    void replayAfterEscrowFailureStatusWriteFaultShouldNotCompensateInventoryTwice() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderRepository orderRepository = mock(MarketOrderRepository.class);
        MarketListingRepository listingRepository = mock(MarketListingRepository.class);
        MarketInventoryRepository inventoryRepository = mock(MarketInventoryRepository.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = escrowAction();
        var walletActionRepository = new MyBatisMarketWalletActionRepository(mapper);
        UUID listingId = uuid(23);
        MarketOrder initialOrder = order(action.getOrderId())
                .listingId(listingId)
                .goodsType("VIRTUAL")
                .sellerUserId(action.getCounterpartyUserId())
                .buyerUserId(action.getActorUserId())
                .quantity(1)
                .deliveryModeSnapshot("PRELOADED")
                .status("ESCROW_PENDING")
                .build();
        AtomicReference<MarketOrder> currentOrder = new AtomicReference<>(initialOrder);
        AtomicInteger stock = new AtomicInteger(0);
        AtomicInteger maximumObservedStock = new AtomicInteger(0);
        AtomicInteger reservedReleaseCalls = new AtomicInteger(0);
        AtomicReference<String> walletActionStatus = new AtomicReference<>(MarketWalletActionStatus.PENDING);
        AtomicReference<Date> processingLeaseUntil = new AtomicReference<>();
        AtomicReference<MarketWalletActionLease> processingLease = new AtomicReference<>();
        MarketListing listing = new MarketListing();
        listing.setListingId(listingId);
        listing.setSellerUserId(action.getCounterpartyUserId());
        listing.setGoodsType("VIRTUAL");
        listing.setStockMode("FINITE");
        listing.setStockAvailable(0);
        listing.setStatus("SOLD_OUT");

        when(orderRepository.findById(action.getOrderId())).thenAnswer(ignored -> currentOrder.get());
        when(orderRepository.lockById(action.getOrderId())).thenAnswer(ignored -> currentOrder.get());
        when(orderRepository.apply(any(MarketOrderTransition.class))).thenAnswer(invocation -> {
            MarketOrderTransition transition = invocation.getArgument(0);
            MarketOrder current = currentOrder.get();
            if (!transition.expectedStatuses().contains(current.status())) {
                return MarketOrderRepository.ApplyStatus.STALE;
            }
            currentOrder.set(order(current).status(transition.nextStatus().code()).build());
            return MarketOrderRepository.ApplyStatus.APPLIED;
        });
        when(listingRepository.lockById(listingId)).thenReturn(listing);
        when(listingRepository.adjustStock(any(), any(), anyInt(), anyInt(), any())).thenAnswer(invocation -> {
            int adjusted = stock.addAndGet(invocation.getArgument(3));
            maximumObservedStock.accumulateAndGet(adjusted, Math::max);
            return 1;
        });
        when(inventoryRepository.releaseReservedByOrderIfNeeded(action.getOrderId())).thenAnswer(ignored -> {
            int call = reservedReleaseCalls.incrementAndGet();
            return call == 1 ? 1 : 0;
        });
        when(mapper.claimProcessing(any(MarketWalletActionLease.class), any())).thenAnswer(invocation -> {
            String status = walletActionStatus.get();
            if (!MarketWalletActionStatus.PENDING.equals(status)
                    && !MarketWalletActionStatus.RETRYING.equals(status)) {
                return 0;
            }
            walletActionStatus.set(MarketWalletActionStatus.PROCESSING);
            processingLease.set(invocation.getArgument(0));
            processingLeaseUntil.set(invocation.getArgument(1));
            return 1;
        });
        when(mapper.recoverExpiredProcessing(any())).thenAnswer(invocation -> {
            Date asOf = invocation.getArgument(0);
            Date leaseUntil = processingLeaseUntil.get();
            if (!MarketWalletActionStatus.PROCESSING.equals(walletActionStatus.get())
                    || leaseUntil == null
                    || leaseUntil.after(asOf)) {
                return 0;
            }
            walletActionStatus.set(MarketWalletActionStatus.RETRYING);
            processingLease.set(null);
            processingLeaseUntil.set(null);
            return 1;
        });
        when(mapper.markCancelled(any(MarketWalletActionLease.class), eq("NOOP"))).thenAnswer(invocation -> {
            if (!MarketWalletActionStatus.PROCESSING.equals(walletActionStatus.get())
                    || !invocation.getArgument(0).equals(processingLease.get())) {
                return 0;
            }
            walletActionStatus.set(MarketWalletActionStatus.CANCELLED);
            processingLease.set(null);
            processingLeaseUntil.set(null);
            return 1;
        });
        when(walletApi.escrowOrder(
                action.getRequestId(),
                action.getActorUserId(),
                action.getAmount(),
                action.getWalletBizId()
        )).thenThrow(new BusinessException(WalletErrorCode.INVALID_REQUEST, "escrow rejected"));
        when(mapper.markFailed(any(MarketWalletActionLease.class), any(), any()))
                .thenThrow(new IllegalStateException("wallet action status write failed"));
        MarketOrderSagaApplicationService sagaService = new MarketOrderSagaApplicationService(
                orderRepository,
                listingRepository,
                inventoryRepository
        );
        Clock clock = Clock.fixed(Instant.parse("2026-05-18T00:00:00Z"), ZoneOffset.UTC);
        MarketWalletActionProcessorApplicationService processor = new MarketWalletActionProcessorApplicationService(
                walletActionRepository,
                walletApi,
                sagaService,
                actionService,
                clock
        );
        MarketWalletActionRecoveryApplicationService recovery = new MarketWalletActionRecoveryApplicationService(
                walletActionRepository,
                orderRepository,
                sagaService,
                actionService,
                clock
        );

        assertThatThrownBy(() -> processor.processOne(action))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("wallet action status write failed");
        assertThat(currentOrder.get().getStatus()).isEqualTo("ESCROW_FAILED");
        assertThat(stock.get()).isEqualTo(1);
        assertThat(reservedReleaseCalls.get()).isEqualTo(1);
        assertThat(walletActionStatus.get()).isEqualTo(MarketWalletActionStatus.PROCESSING);
        Date firstProcessingLease = processingLeaseUntil.get();
        assertThat(firstProcessingLease.toInstant()).isEqualTo(clock.instant().plusSeconds(60));

        assertThat(processor.processOne(action)).isFalse();
        assertThat(walletActionStatus.get()).isEqualTo(MarketWalletActionStatus.PROCESSING);
        assertThat(processingLeaseUntil.get()).isEqualTo(firstProcessingLease);

        assertThat(recovery.recoverExpiredProcessing(clock.instant())).isZero();
        assertThat(walletActionStatus.get()).isEqualTo(MarketWalletActionStatus.PROCESSING);
        assertThat(processingLeaseUntil.get()).isEqualTo(firstProcessingLease);

        assertThat(recovery.recoverExpiredProcessing(clock.instant().plusSeconds(61))).isEqualTo(1);
        assertThat(walletActionStatus.get()).isEqualTo(MarketWalletActionStatus.RETRYING);
        assertThat(processingLeaseUntil.get()).isNull();
        assertThat(processor.processOne(action)).isTrue();

        assertThat(currentOrder.get().getStatus()).isEqualTo("CANCELLED");
        assertThat(walletActionStatus.get()).isEqualTo(MarketWalletActionStatus.CANCELLED);
        assertThat(processingLeaseUntil.get()).isNull();
        assertThat(stock.get()).isEqualTo(1);
        assertThat(maximumObservedStock.get()).isLessThanOrEqualTo(1);
        assertThat(reservedReleaseCalls.get()).isEqualTo(1);
    }

    @Test
    void processOneShouldRetryReleaseRecoverablyWhenWalletRejectsBusinessFailure() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = releaseAction();
        when(mapper.claimProcessing(any(MarketWalletActionLease.class), any())).thenReturn(1);
        when(mapper.markRetrying(any(MarketWalletActionLease.class), any(), any())).thenReturn(1);
        when(walletApi.releaseOrder(action.getRequestId(), action.getActorUserId(), action.getAmount(), action.getWalletBizId()))
                .thenThrow(new BusinessException(WalletErrorCode.ACCOUNT_BALANCE_INSUFFICIENT, "escrow insufficient"));

        MarketWalletActionProcessorApplicationService processor = new MarketWalletActionProcessorApplicationService(
                new MyBatisMarketWalletActionRepository(mapper),
                walletApi,
                sagaService,
                actionService,
                Clock.systemUTC()
        );

        boolean processed = processor.processOne(action);

        assertThat(processed).isFalse();
        ArgumentCaptor<MarketWalletActionLease> leaseCaptor =
                ArgumentCaptor.forClass(MarketWalletActionLease.class);
        verify(mapper).claimProcessing(leaseCaptor.capture(), any());
        verify(mapper).markRetrying(
                same(leaseCaptor.getValue()),
                any(),
                eq("escrow insufficient")
        );
        verify(sagaService, never()).markReleaseSucceeded(any(), any());
        verify(mapper, never()).markFailed(any(MarketWalletActionLease.class), any(), any());
        verify(mapper, never()).markSucceeded(any(MarketWalletActionLease.class), any(), any());
    }

    @Test
    void processOneShouldRetryRefundRecoverablyWhenWalletRejectsBusinessFailure() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = refundAction();
        when(mapper.claimProcessing(any(MarketWalletActionLease.class), any())).thenReturn(1);
        when(mapper.markRetrying(any(MarketWalletActionLease.class), any(), any())).thenReturn(1);
        when(walletApi.refundOrder(action.getRequestId(), action.getActorUserId(), action.getAmount(), action.getWalletBizId()))
                .thenThrow(new BusinessException(WalletErrorCode.ACCOUNT_BALANCE_INSUFFICIENT, "escrow insufficient"));

        MarketWalletActionProcessorApplicationService processor = new MarketWalletActionProcessorApplicationService(
                new MyBatisMarketWalletActionRepository(mapper),
                walletApi,
                sagaService,
                actionService,
                Clock.systemUTC()
        );

        boolean processed = processor.processOne(action);

        assertThat(processed).isFalse();
        ArgumentCaptor<MarketWalletActionLease> leaseCaptor =
                ArgumentCaptor.forClass(MarketWalletActionLease.class);
        verify(mapper).claimProcessing(leaseCaptor.capture(), any());
        verify(mapper).markRetrying(
                same(leaseCaptor.getValue()),
                any(),
                eq("escrow insufficient")
        );
        verify(sagaService, never()).markRefundSucceeded(any(), any());
        verify(mapper, never()).markFailed(any(MarketWalletActionLease.class), any(), any());
        verify(mapper, never()).markSucceeded(any(MarketWalletActionLease.class), any(), any());
    }

    @Test
    void processOneShouldMarkReleaseDeadWhenRecoverableFailureExceedsRetryBudget() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = releaseAction();
        action.setRetryCount(7);
        when(mapper.claimProcessing(any(MarketWalletActionLease.class), any())).thenReturn(1);
        when(mapper.markDead(any(MarketWalletActionLease.class), any())).thenReturn(1);
        when(walletApi.releaseOrder(action.getRequestId(), action.getActorUserId(), action.getAmount(), action.getWalletBizId()))
                .thenThrow(new BusinessException(WalletErrorCode.ACCOUNT_BALANCE_INSUFFICIENT, "escrow insufficient"));

        MarketWalletActionProcessorApplicationService processor = new MarketWalletActionProcessorApplicationService(
                new MyBatisMarketWalletActionRepository(mapper),
                walletApi,
                sagaService,
                actionService,
                Clock.systemUTC(),
                Duration.ofSeconds(60),
                8
        );

        boolean processed = processor.processOne(action);

        assertThat(processed).isTrue();
        ArgumentCaptor<MarketWalletActionLease> leaseCaptor =
                ArgumentCaptor.forClass(MarketWalletActionLease.class);
        verify(mapper).claimProcessing(leaseCaptor.capture(), any());
        verify(mapper).markDead(same(leaseCaptor.getValue()), eq("escrow insufficient"));
        verify(mapper, never()).markRetrying(any(MarketWalletActionLease.class), any(), any());
        verify(mapper, never()).markFailed(any(MarketWalletActionLease.class), any(), any());
    }

    @Test
    void processOneShouldFailReleasePermanentlyWhenWalletRejectsInvalidRequest() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = releaseAction();
        when(mapper.claimProcessing(any(MarketWalletActionLease.class), any())).thenReturn(1);
        when(mapper.markFailed(any(MarketWalletActionLease.class), any(), any())).thenReturn(1);
        when(walletApi.releaseOrder(action.getRequestId(), action.getActorUserId(), action.getAmount(), action.getWalletBizId()))
                .thenThrow(new BusinessException(WalletErrorCode.INVALID_REQUEST, "invalid market wallet request"));

        MarketWalletActionProcessorApplicationService processor = new MarketWalletActionProcessorApplicationService(
                new MyBatisMarketWalletActionRepository(mapper),
                walletApi,
                sagaService,
                actionService,
                Clock.systemUTC()
        );

        boolean processed = processor.processOne(action);

        assertThat(processed).isTrue();
        ArgumentCaptor<MarketWalletActionLease> leaseCaptor =
                ArgumentCaptor.forClass(MarketWalletActionLease.class);
        verify(mapper).claimProcessing(leaseCaptor.capture(), any());
        verify(mapper).markFailed(
                same(leaseCaptor.getValue()),
                eq(String.valueOf(WalletErrorCode.INVALID_REQUEST.getCode())),
                eq("invalid market wallet request")
        );
        verify(mapper, never()).markRetrying(any(MarketWalletActionLease.class), any(), any());
        verify(sagaService, never()).markReleaseSucceeded(any(), any());
    }

    @Test
    void processOneShouldUseClaimedLeaseForSuccessfulTransition() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = releaseAction();
        var walletTxn = new com.nowcoder.community.wallet.api.model.WalletMarketTxnView(
                uuid(100),
                "ORDER_RELEASE",
                "SUCCEEDED",
                action.getAmount(),
                action.getWalletBizId()
        );
        when(mapper.claimProcessing(any(MarketWalletActionLease.class), any())).thenReturn(1);
        when(walletApi.releaseOrder(
                action.getRequestId(),
                action.getActorUserId(),
                action.getAmount(),
                action.getWalletBizId()
        )).thenReturn(walletTxn);
        when(sagaService.markReleaseSucceeded(action.getOrderId(), walletTxn.txnId())).thenReturn(true);
        when(mapper.markSucceeded(any(MarketWalletActionLease.class), eq(walletTxn.txnId()), eq("APPLIED")))
                .thenReturn(1);
        MarketWalletActionProcessorApplicationService processor = new MarketWalletActionProcessorApplicationService(
                new MyBatisMarketWalletActionRepository(mapper),
                walletApi,
                sagaService,
                actionService,
                Clock.systemUTC()
        );

        boolean processed = processor.processOne(action);

        assertThat(processed).isTrue();
        ArgumentCaptor<MarketWalletActionLease> leaseCaptor =
                ArgumentCaptor.forClass(MarketWalletActionLease.class);
        verify(mapper).claimProcessing(leaseCaptor.capture(), any());
        verify(mapper).markSucceeded(
                same(leaseCaptor.getValue()),
                eq(walletTxn.txnId()),
                eq("APPLIED")
        );
    }

    @Test
    void processOneShouldLeaveReleaseActionRecoverableWhenSagaStateDoesNotAdvance() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = releaseAction();
        var walletTxn = new com.nowcoder.community.wallet.api.model.WalletMarketTxnView(
                uuid(101),
                "ORDER_RELEASE",
                "SUCCEEDED",
                action.getAmount(),
                action.getWalletBizId()
        );
        when(mapper.claimProcessing(any(MarketWalletActionLease.class), any())).thenReturn(1);
        when(mapper.markRecoveryPending(any(MarketWalletActionLease.class), any(), any(), any()))
                .thenReturn(1);
        when(walletApi.releaseOrder(action.getRequestId(), action.getActorUserId(), action.getAmount(), action.getWalletBizId()))
                .thenReturn(walletTxn);
        when(sagaService.markReleaseSucceeded(action.getOrderId(), walletTxn.txnId())).thenReturn(false);

        MarketWalletActionProcessorApplicationService processor = new MarketWalletActionProcessorApplicationService(
                new MyBatisMarketWalletActionRepository(mapper),
                walletApi,
                sagaService,
                actionService,
                Clock.systemUTC()
        );

        boolean processed = processor.processOne(action);

        assertThat(processed).isTrue();
        verify(sagaService).markReleaseSucceeded(action.getOrderId(), walletTxn.txnId());
        ArgumentCaptor<MarketWalletActionLease> leaseCaptor =
                ArgumentCaptor.forClass(MarketWalletActionLease.class);
        verify(mapper).claimProcessing(leaseCaptor.capture(), any());
        verify(mapper, never()).markSucceeded(any(MarketWalletActionLease.class), any(), any());
        verify(mapper).markRecoveryPending(
                same(leaseCaptor.getValue()),
                eq(walletTxn.txnId()),
                eq("SAGA_STATE_NOT_ADVANCED"),
                eq("market order saga did not advance after wallet success")
        );
    }

    @Test
    void processOneShouldLeaveEscrowActionRecoverableWhenWalletSucceedsButSagaCannotApply() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = escrowAction();
        var walletTxn = new com.nowcoder.community.wallet.api.model.WalletMarketTxnView(
                uuid(102),
                "ORDER_ESCROW",
                "SUCCEEDED",
                action.getAmount(),
                action.getWalletBizId()
        );
        when(mapper.claimProcessing(any(MarketWalletActionLease.class), any())).thenReturn(1);
        when(mapper.markRecoveryPending(any(MarketWalletActionLease.class), any(), any(), any()))
                .thenReturn(1);
        when(sagaService.canApplyEscrow(action.getOrderId())).thenReturn(true);
        when(walletApi.escrowOrder(action.getRequestId(), action.getActorUserId(), action.getAmount(), action.getWalletBizId()))
                .thenReturn(walletTxn);
        when(sagaService.markEscrowSucceeded(action.getOrderId(), walletTxn.txnId())).thenReturn(false);
        when(sagaService.markEscrowCancelRefundPending(action.getOrderId(), walletTxn.txnId())).thenReturn(false);

        MarketWalletActionProcessorApplicationService processor = new MarketWalletActionProcessorApplicationService(
                new MyBatisMarketWalletActionRepository(mapper),
                walletApi,
                sagaService,
                actionService,
                Clock.systemUTC()
        );

        boolean processed = processor.processOne(action);

        assertThat(processed).isTrue();
        verify(sagaService).markEscrowSucceeded(action.getOrderId(), walletTxn.txnId());
        verify(sagaService).markEscrowCancelRefundPending(action.getOrderId(), walletTxn.txnId());
        ArgumentCaptor<MarketWalletActionLease> leaseCaptor =
                ArgumentCaptor.forClass(MarketWalletActionLease.class);
        verify(mapper).claimProcessing(leaseCaptor.capture(), any());
        verify(mapper, never()).markSucceeded(any(MarketWalletActionLease.class), any(), any());
        verify(mapper).markRecoveryPending(
                same(leaseCaptor.getValue()),
                eq(walletTxn.txnId()),
                eq("SAGA_STATE_NOT_ADVANCED"),
                eq("market order saga did not advance after wallet success")
        );
        verify(actionService, never()).enqueueRefund(any(), any(), any(), anyLong());
    }

    @Test
    void processOneShouldStopWhenSucceededTransitionLosesLease() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = releaseAction();
        var walletTxn = new com.nowcoder.community.wallet.api.model.WalletMarketTxnView(
                uuid(103),
                "ORDER_RELEASE",
                "SUCCEEDED",
                action.getAmount(),
                action.getWalletBizId()
        );
        when(mapper.claimProcessing(any(MarketWalletActionLease.class), any())).thenReturn(1);
        when(walletApi.releaseOrder(
                action.getRequestId(),
                action.getActorUserId(),
                action.getAmount(),
                action.getWalletBizId()
        )).thenReturn(walletTxn);
        when(sagaService.markReleaseSucceeded(action.getOrderId(), walletTxn.txnId())).thenReturn(true);
        when(mapper.markSucceeded(any(MarketWalletActionLease.class), eq(walletTxn.txnId()), eq("APPLIED")))
                .thenReturn(0);
        MarketWalletActionProcessorApplicationService processor = new MarketWalletActionProcessorApplicationService(
                new MyBatisMarketWalletActionRepository(mapper),
                walletApi,
                sagaService,
                actionService,
                Clock.systemUTC()
        );

        assertThat(processor.processOne(action)).isFalse();

        verify(mapper).markSucceeded(any(MarketWalletActionLease.class), eq(walletTxn.txnId()), eq("APPLIED"));
        verify(mapper, never()).markRecoveryPending(any(MarketWalletActionLease.class), any(), any(), any());
        verify(mapper, never()).markFailed(any(MarketWalletActionLease.class), any(), any());
    }

    @Test
    void processOneShouldStopBeforeCompletingNoopWhenCancelledTransitionLosesLease() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = escrowAction();
        when(mapper.claimProcessing(any(MarketWalletActionLease.class), any())).thenReturn(1);
        when(sagaService.canApplyEscrow(action.getOrderId())).thenReturn(false);
        when(mapper.markCancelled(any(MarketWalletActionLease.class), eq("NOOP"))).thenReturn(0);
        MarketWalletActionProcessorApplicationService processor = new MarketWalletActionProcessorApplicationService(
                new MyBatisMarketWalletActionRepository(mapper),
                walletApi,
                sagaService,
                actionService,
                Clock.systemUTC()
        );

        assertThat(processor.processOne(action)).isFalse();

        verify(mapper).markCancelled(any(MarketWalletActionLease.class), eq("NOOP"));
        verify(sagaService, never()).completeEscrowNoop(any());
        verify(mapper, never()).markFailed(any(MarketWalletActionLease.class), any(), any());
    }

    @Test
    void processOneShouldNotFallbackWhenRetryTransitionLosesLease() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = releaseAction();
        when(mapper.claimProcessing(any(MarketWalletActionLease.class), any())).thenReturn(1);
        when(walletApi.releaseOrder(
                action.getRequestId(),
                action.getActorUserId(),
                action.getAmount(),
                action.getWalletBizId()
        )).thenThrow(new BusinessException(
                WalletErrorCode.ACCOUNT_BALANCE_INSUFFICIENT,
                "escrow insufficient"
        ));
        when(mapper.markRetrying(any(MarketWalletActionLease.class), any(), any())).thenReturn(0);
        MarketWalletActionProcessorApplicationService processor = new MarketWalletActionProcessorApplicationService(
                new MyBatisMarketWalletActionRepository(mapper),
                walletApi,
                sagaService,
                actionService,
                Clock.systemUTC()
        );

        assertThat(processor.processOne(action)).isFalse();

        verify(mapper).markRetrying(any(MarketWalletActionLease.class), any(), eq("escrow insufficient"));
        verify(mapper, never()).markFailed(any(MarketWalletActionLease.class), any(), any());
        verify(mapper, never()).markDead(any(MarketWalletActionLease.class), any());
    }

    @Test
    void processOneShouldNotFallbackWhenFailedTransitionLosesLease() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = releaseAction();
        when(mapper.claimProcessing(any(MarketWalletActionLease.class), any())).thenReturn(1);
        when(walletApi.releaseOrder(
                action.getRequestId(),
                action.getActorUserId(),
                action.getAmount(),
                action.getWalletBizId()
        )).thenThrow(new BusinessException(WalletErrorCode.INVALID_REQUEST, "invalid request"));
        when(mapper.markFailed(any(MarketWalletActionLease.class), any(), any())).thenReturn(0);
        MarketWalletActionProcessorApplicationService processor = new MarketWalletActionProcessorApplicationService(
                new MyBatisMarketWalletActionRepository(mapper),
                walletApi,
                sagaService,
                actionService,
                Clock.systemUTC()
        );

        assertThat(processor.processOne(action)).isFalse();

        verify(mapper).markFailed(
                any(MarketWalletActionLease.class),
                eq(String.valueOf(WalletErrorCode.INVALID_REQUEST.getCode())),
                eq("invalid request")
        );
        verify(mapper, never()).markRetrying(any(MarketWalletActionLease.class), any(), any());
        verify(mapper, never()).markDead(any(MarketWalletActionLease.class), any());
    }

    @Test
    void processOneShouldNotFallbackWhenRecoveryPendingTransitionLosesLease() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = releaseAction();
        var walletTxn = new com.nowcoder.community.wallet.api.model.WalletMarketTxnView(
                uuid(104),
                "ORDER_RELEASE",
                "SUCCEEDED",
                action.getAmount(),
                action.getWalletBizId()
        );
        when(mapper.claimProcessing(any(MarketWalletActionLease.class), any())).thenReturn(1);
        when(walletApi.releaseOrder(
                action.getRequestId(),
                action.getActorUserId(),
                action.getAmount(),
                action.getWalletBizId()
        )).thenReturn(walletTxn);
        when(sagaService.markReleaseSucceeded(action.getOrderId(), walletTxn.txnId())).thenReturn(false);
        when(mapper.markRecoveryPending(any(MarketWalletActionLease.class), any(), any(), any()))
                .thenReturn(0);
        MarketWalletActionProcessorApplicationService processor = new MarketWalletActionProcessorApplicationService(
                new MyBatisMarketWalletActionRepository(mapper),
                walletApi,
                sagaService,
                actionService,
                Clock.systemUTC()
        );

        assertThat(processor.processOne(action)).isFalse();

        verify(mapper).markRecoveryPending(
                any(MarketWalletActionLease.class),
                eq(walletTxn.txnId()),
                eq("SAGA_STATE_NOT_ADVANCED"),
                eq("market order saga did not advance after wallet success")
        );
        verify(mapper, never()).markSucceeded(any(MarketWalletActionLease.class), any(), any());
        verify(mapper, never()).markFailed(any(MarketWalletActionLease.class), any(), any());
    }

    @Test
    void processOneShouldNotFallbackWhenDeadTransitionLosesLease() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = releaseAction();
        action.setRetryCount(7);
        when(mapper.claimProcessing(any(MarketWalletActionLease.class), any())).thenReturn(1);
        when(walletApi.releaseOrder(
                action.getRequestId(),
                action.getActorUserId(),
                action.getAmount(),
                action.getWalletBizId()
        )).thenThrow(new BusinessException(
                WalletErrorCode.ACCOUNT_BALANCE_INSUFFICIENT,
                "escrow insufficient"
        ));
        when(mapper.markDead(any(MarketWalletActionLease.class), any())).thenReturn(0);
        MarketWalletActionProcessorApplicationService processor = new MarketWalletActionProcessorApplicationService(
                new MyBatisMarketWalletActionRepository(mapper),
                walletApi,
                sagaService,
                actionService,
                Clock.systemUTC(),
                Duration.ofSeconds(60),
                8
        );

        assertThat(processor.processOne(action)).isFalse();

        verify(mapper).markDead(any(MarketWalletActionLease.class), eq("escrow insufficient"));
        verify(mapper, never()).markRetrying(any(MarketWalletActionLease.class), any(), any());
        verify(mapper, never()).markFailed(any(MarketWalletActionLease.class), any(), any());
    }

    @Test
    void staleWorkerShouldNotOverwriteReplacementWorkerAfterRecovery() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderRepository orderRepository = mock(MarketOrderRepository.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = releaseAction();
        var walletTxn = new com.nowcoder.community.wallet.api.model.WalletMarketTxnView(
                uuid(105),
                "ORDER_RELEASE",
                "SUCCEEDED",
                action.getAmount(),
                action.getWalletBizId()
        );
        AtomicReference<String> status = new AtomicReference<>(MarketWalletActionStatus.PENDING);
        AtomicReference<MarketWalletActionLease> currentLease = new AtomicReference<>();
        AtomicReference<MarketWalletActionLease> leaseA = new AtomicReference<>();
        AtomicReference<MarketWalletActionLease> leaseB = new AtomicReference<>();
        AtomicInteger claimCount = new AtomicInteger();
        when(mapper.claimProcessing(any(MarketWalletActionLease.class), any())).thenAnswer(invocation -> {
            if (!MarketWalletActionStatus.PENDING.equals(status.get())
                    && !MarketWalletActionStatus.RETRYING.equals(status.get())) {
                return 0;
            }
            MarketWalletActionLease lease = invocation.getArgument(0);
            if (claimCount.getAndIncrement() == 0) {
                leaseA.set(lease);
            } else {
                leaseB.set(lease);
            }
            currentLease.set(lease);
            status.set(MarketWalletActionStatus.PROCESSING);
            return 1;
        });
        when(mapper.recoverExpiredProcessing(any())).thenAnswer(ignored -> {
            if (!MarketWalletActionStatus.PROCESSING.equals(status.get())) {
                return 0;
            }
            status.set(MarketWalletActionStatus.RETRYING);
            currentLease.set(null);
            return 1;
        });
        when(mapper.markSucceeded(any(MarketWalletActionLease.class), eq(walletTxn.txnId()), eq("APPLIED")))
                .thenAnswer(invocation -> {
                    MarketWalletActionLease attemptedLease = invocation.getArgument(0);
                    if (!MarketWalletActionStatus.PROCESSING.equals(status.get())
                            || !attemptedLease.equals(currentLease.get())) {
                        return 0;
                    }
                    status.set(MarketWalletActionStatus.SUCCEEDED);
                    currentLease.set(null);
                    return 1;
                });
        when(walletApi.releaseOrder(
                action.getRequestId(),
                action.getActorUserId(),
                action.getAmount(),
                action.getWalletBizId()
        )).thenReturn(walletTxn);
        var walletActionRepository = new MyBatisMarketWalletActionRepository(mapper);
        Clock clock = Clock.fixed(Instant.parse("2026-05-18T00:00:00Z"), ZoneOffset.UTC);
        MarketWalletActionProcessorApplicationService workerA = new MarketWalletActionProcessorApplicationService(
                walletActionRepository,
                walletApi,
                sagaService,
                actionService,
                clock
        );
        MarketWalletActionProcessorApplicationService workerB = new MarketWalletActionProcessorApplicationService(
                walletActionRepository,
                walletApi,
                sagaService,
                actionService,
                clock
        );
        MarketWalletActionRecoveryApplicationService recovery = new MarketWalletActionRecoveryApplicationService(
                walletActionRepository,
                orderRepository,
                sagaService,
                actionService,
                clock
        );
        AtomicInteger sagaCallCount = new AtomicInteger();
        AtomicReference<Boolean> workerBProcessed = new AtomicReference<>();
        when(sagaService.markReleaseSucceeded(action.getOrderId(), walletTxn.txnId())).thenAnswer(ignored -> {
            if (sagaCallCount.getAndIncrement() == 0) {
                assertThat(recovery.recoverExpiredProcessing(clock.instant().plusSeconds(61))).isEqualTo(1);
                workerBProcessed.set(workerB.processOne(action));
            }
            return true;
        });

        boolean workerAProcessed = workerA.processOne(action);

        assertThat(workerBProcessed.get()).isTrue();
        assertThat(workerAProcessed).isFalse();
        assertThat(status.get()).isEqualTo(MarketWalletActionStatus.SUCCEEDED);
        assertThat(leaseA.get().actionId()).isEqualTo(action.getActionId());
        assertThat(leaseB.get().actionId()).isEqualTo(action.getActionId());
        assertThat(leaseA.get().token()).isNotEqualTo(leaseB.get().token());
        ArgumentCaptor<MarketWalletActionLease> transitionLeases =
                ArgumentCaptor.forClass(MarketWalletActionLease.class);
        verify(mapper, times(2)).markSucceeded(
                transitionLeases.capture(),
                eq(walletTxn.txnId()),
                eq("APPLIED")
        );
        assertThat(transitionLeases.getAllValues()).containsExactly(leaseB.get(), leaseA.get());
        verify(mapper, never()).markFailed(any(MarketWalletActionLease.class), any(), any());
        verify(mapper, never()).markRecoveryPending(any(MarketWalletActionLease.class), any(), any(), any());
    }

    private MarketWalletAction escrowAction() {
        MarketWalletAction action = new MarketWalletAction();
        action.setActionId(uuid(11));
        action.setOrderId(uuid(22));
        action.setActionType("ESCROW");
        action.setRequestId("market-order:" + action.getOrderId() + ":escrow");
        action.setWalletBizId("market-order:" + action.getOrderId());
        action.setActorUserId(uuid(9));
        action.setCounterpartyUserId(uuid(7));
        action.setAmount(12_900L);
        action.setStatus("PENDING");
        return action;
    }

    private MarketWalletAction releaseAction() {
        MarketWalletAction action = escrowAction();
        action.setActionId(uuid(12));
        action.setActionType("RELEASE");
        action.setRequestId("market-order:" + action.getOrderId() + ":release");
        action.setActorUserId(uuid(7));
        action.setCounterpartyUserId(uuid(9));
        return action;
    }

    private MarketWalletAction refundAction() {
        MarketWalletAction action = escrowAction();
        action.setActionId(uuid(13));
        action.setActionType("REFUND");
        action.setRequestId("market-order:" + action.getOrderId() + ":refund");
        return action;
    }
}
