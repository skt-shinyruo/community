package com.nowcoder.community.market.application;

import com.nowcoder.community.market.domain.model.MarketWalletAction;
import com.nowcoder.community.market.domain.model.MarketWalletActionType;

import java.util.UUID;

final class MarketWalletTxnSagaApplier {

    private final MarketOrderSagaApplicationService sagaService;
    private final MarketWalletActionCoordinator actionCoordinator;

    MarketWalletTxnSagaApplier(
            MarketOrderSagaApplicationService sagaService,
            MarketWalletActionCoordinator actionCoordinator
    ) {
        this.sagaService = sagaService;
        this.actionCoordinator = actionCoordinator;
    }

    boolean apply(MarketWalletAction action, UUID walletTxnId) {
        if (MarketWalletActionType.ESCROW.equals(action.getActionType())) {
            if (sagaService.markEscrowSucceeded(action.getOrderId(), walletTxnId)) {
                return true;
            }
            if (sagaService.markEscrowCancelRefundPending(action.getOrderId(), walletTxnId)) {
                actionCoordinator.enqueueRefund(
                        action.getOrderId(),
                        action.getActorUserId(),
                        action.getCounterpartyUserId(),
                        action.getAmount()
                );
                return true;
            }
            return false;
        }
        if (MarketWalletActionType.RELEASE.equals(action.getActionType())) {
            return sagaService.markReleaseSucceeded(action.getOrderId(), walletTxnId);
        }
        if (MarketWalletActionType.REFUND.equals(action.getActionType())) {
            return sagaService.markRefundSucceeded(action.getOrderId(), walletTxnId);
        }
        return false;
    }
}
