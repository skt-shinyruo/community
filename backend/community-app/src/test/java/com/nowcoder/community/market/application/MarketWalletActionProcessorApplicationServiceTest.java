package com.nowcoder.community.market.application;

import com.nowcoder.community.market.domain.model.MarketWalletAction;
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

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        when(mapper.claimProcessing(eq(action.getActionId()), any())).thenReturn(0);
        MarketWalletActionProcessorApplicationService processor = new MarketWalletActionProcessorApplicationService(
                new MyBatisMarketWalletActionRepository(mapper),
                walletApi,
                sagaService,
                actionService,
                Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofSeconds(120)
        );

        processor.processOne(action);

        ArgumentCaptor<Date> leaseCaptor = ArgumentCaptor.forClass(Date.class);
        verify(mapper).claimProcessing(eq(action.getActionId()), leaseCaptor.capture());
        assertThat(leaseCaptor.getValue().toInstant()).isEqualTo(now.plusSeconds(120));
    }

    @Test
    void processOneShouldNoopEscrowWhenSagaRejectsForwardAction() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = escrowAction();
        when(mapper.claimProcessing(eq(action.getActionId()), any())).thenReturn(1);
        when(sagaService.canApplyEscrow(action.getOrderId())).thenReturn(false);

        MarketWalletActionProcessorApplicationService processor = new MarketWalletActionProcessorApplicationService(
                new MyBatisMarketWalletActionRepository(mapper),
                walletApi,
                sagaService,
                actionService,
                Clock.systemUTC()
        );

        processor.processOne(action);

        verify(walletApi, never()).escrowOrder(any(), any(), anyLong(), any());
        verify(mapper).markCancelled(action.getActionId(), "NOOP");
    }

    @Test
    void processOneShouldRetryReleaseRecoverablyWhenWalletRejectsBusinessFailure() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = releaseAction();
        when(mapper.claimProcessing(eq(action.getActionId()), any())).thenReturn(1);
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
        verify(mapper).markRetrying(
                eq(action.getActionId()),
                any(),
                eq("escrow insufficient")
        );
        verify(sagaService, never()).markReleaseSucceeded(any(), any());
        verify(mapper, never()).markFailed(any(), any(), any());
        verify(mapper, never()).markSucceeded(any(), any(), any());
    }

    @Test
    void processOneShouldRetryRefundRecoverablyWhenWalletRejectsBusinessFailure() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = refundAction();
        when(mapper.claimProcessing(eq(action.getActionId()), any())).thenReturn(1);
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
        verify(mapper).markRetrying(
                eq(action.getActionId()),
                any(),
                eq("escrow insufficient")
        );
        verify(sagaService, never()).markRefundSucceeded(any(), any());
        verify(mapper, never()).markFailed(any(), any(), any());
        verify(mapper, never()).markSucceeded(any(), any(), any());
    }

    @Test
    void processOneShouldMarkReleaseDeadWhenRecoverableFailureExceedsRetryBudget() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = releaseAction();
        action.setRetryCount(7);
        when(mapper.claimProcessing(eq(action.getActionId()), any())).thenReturn(1);
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
        verify(mapper).markDead(action.getActionId(), "escrow insufficient");
        verify(mapper, never()).markRetrying(any(), any(), any());
        verify(mapper, never()).markFailed(any(), any(), any());
    }

    @Test
    void processOneShouldFailReleasePermanentlyWhenWalletRejectsInvalidRequest() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionApplicationService actionService = mock(MarketWalletActionApplicationService.class);
        WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
        MarketWalletAction action = releaseAction();
        when(mapper.claimProcessing(eq(action.getActionId()), any())).thenReturn(1);
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
        verify(mapper).markFailed(
                eq(action.getActionId()),
                eq(String.valueOf(WalletErrorCode.INVALID_REQUEST.getCode())),
                eq("invalid market wallet request")
        );
        verify(mapper, never()).markRetrying(any(), any(), any());
        verify(sagaService, never()).markReleaseSucceeded(any(), any());
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
        when(mapper.claimProcessing(eq(action.getActionId()), any())).thenReturn(1);
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
        verify(mapper, never()).markSucceeded(any(), any(), any());
        verify(mapper).markRecoveryPending(
                eq(action.getActionId()),
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
        when(mapper.claimProcessing(eq(action.getActionId()), any())).thenReturn(1);
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
        verify(mapper, never()).markSucceeded(any(), any(), any());
        verify(mapper).markRecoveryPending(
                eq(action.getActionId()),
                eq(walletTxn.txnId()),
                eq("SAGA_STATE_NOT_ADVANCED"),
                eq("market order saga did not advance after wallet success")
        );
        verify(actionService, never()).enqueueRefund(any(), any(), any(), anyLong());
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
