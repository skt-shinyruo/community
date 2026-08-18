package com.nowcoder.community.market.application;

import com.nowcoder.community.market.domain.model.MarketWalletAction;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MarketWalletTxnSagaApplierTest {

    @Test
    void lateEscrowSuccessShouldAdvanceCancellationAndEnqueueRefund() {
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionCoordinator actionCoordinator = mock(MarketWalletActionCoordinator.class);
        MarketWalletAction action = escrowAction();
        UUID walletTxnId = uuid(706);
        when(sagaService.markEscrowSucceeded(action.getOrderId(), walletTxnId)).thenReturn(false);
        when(sagaService.markEscrowCancelRefundPending(action.getOrderId(), walletTxnId)).thenReturn(true);
        MarketWalletTxnSagaApplier applier = new MarketWalletTxnSagaApplier(sagaService, actionCoordinator);

        assertThat(applier.apply(action, walletTxnId)).isTrue();

        verify(actionCoordinator).enqueueRefund(
                action.getOrderId(),
                action.getActorUserId(),
                action.getCounterpartyUserId(),
                action.getAmount()
        );
    }

    @Test
    void unsupportedActionShouldRemainUnappliedForRecovery() {
        MarketOrderSagaApplicationService sagaService = mock(MarketOrderSagaApplicationService.class);
        MarketWalletActionCoordinator actionCoordinator = mock(MarketWalletActionCoordinator.class);
        MarketWalletAction action = escrowAction();
        action.setActionType("UNKNOWN");
        MarketWalletTxnSagaApplier applier = new MarketWalletTxnSagaApplier(sagaService, actionCoordinator);

        assertThat(applier.apply(action, uuid(707))).isFalse();

        verifyNoInteractions(sagaService, actionCoordinator);
    }

    private MarketWalletAction escrowAction() {
        MarketWalletAction action = new MarketWalletAction();
        action.setOrderId(uuid(701));
        action.setActionType("ESCROW");
        action.setActorUserId(uuid(702));
        action.setCounterpartyUserId(uuid(703));
        action.setAmount(12_900L);
        return action;
    }
}
