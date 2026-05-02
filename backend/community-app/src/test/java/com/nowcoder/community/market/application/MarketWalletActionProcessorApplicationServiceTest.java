package com.nowcoder.community.market.application;

import com.nowcoder.community.market.domain.model.MarketWalletAction;
import com.nowcoder.community.market.infrastructure.persistence.MyBatisMarketWalletActionRepository;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketWalletActionMapper;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.api.action.WalletMarketActionApi;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;

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
