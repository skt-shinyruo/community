package com.nowcoder.community.market.application;

import com.nowcoder.community.market.domain.model.MarketWalletAction;
import com.nowcoder.community.market.domain.model.MarketWalletActionLease;
import com.nowcoder.community.market.domain.model.MarketWalletActionResultType;
import com.nowcoder.community.market.domain.model.MarketWalletActionType;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import com.nowcoder.community.market.domain.repository.MarketWalletActionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

@Component
public class MarketWalletActionProcessorTransactionOperations {

    static final class LeaseLostException extends RuntimeException {
        LeaseLostException(String message) {
            super(message);
        }
    }

    private static final String SAGA_STATE_NOT_ADVANCED = "SAGA_STATE_NOT_ADVANCED";
    private static final String SAGA_STATE_NOT_ADVANCED_MESSAGE =
            "market order saga did not advance after wallet success";

    private final MarketWalletActionRepository walletActionRepository;
    private final MarketOrderRepository orderRepository;
    private final MarketOrderSagaApplicationService sagaService;
    private final MarketWalletActionCoordinator actionCoordinator;

    @Autowired
    public MarketWalletActionProcessorTransactionOperations(
            MarketWalletActionRepository walletActionRepository,
            MarketOrderRepository orderRepository,
            MarketOrderSagaApplicationService sagaService,
            MarketWalletActionCoordinator actionCoordinator
    ) {
        this.walletActionRepository = walletActionRepository;
        this.orderRepository = orderRepository;
        this.sagaService = sagaService;
        this.actionCoordinator = actionCoordinator;
    }

    MarketWalletActionProcessorTransactionOperations(
            MarketWalletActionRepository walletActionRepository,
            MarketOrderSagaApplicationService sagaService,
            MarketWalletActionCoordinator actionCoordinator
    ) {
        this(walletActionRepository, null, sagaService, actionCoordinator);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordWalletTxn(MarketWalletActionLease lease, UUID walletTxnId, Date leaseValidAt) {
        return walletActionRepository.recordWalletTxn(lease, walletTxnId, leaseValidAt) == 1;
    }

    public boolean canApplyEscrow(UUID orderId) {
        return sagaService.canApplyEscrow(orderId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeEscrowNoop(
            MarketWalletAction action,
            MarketWalletActionLease lease,
            Date leaseValidAt
    ) {
        lockOrderFirst(action);
        if (walletActionRepository.lockClaimed(lease, leaseValidAt) == null) {
            return false;
        }
        if (walletActionRepository.markCancelled(lease, MarketWalletActionResultType.NOOP) != 1) {
            return false;
        }
        sagaService.completeEscrowNoop(action.getOrderId());
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeEscrowTerminalFailure(
            MarketWalletAction action,
            MarketWalletActionLease lease,
            String failureCode,
            String lastError,
            Date leaseValidAt
    ) {
        lockOrderFirst(action);
        if (walletActionRepository.lockClaimed(lease, leaseValidAt) == null) {
            return false;
        }
        sagaService.markEscrowTerminalFailed(action.getOrderId(), lastError);
        int updated = walletActionRepository.markFailed(lease, failureCode, lastError);
        if (updated != 1) {
            throw new LeaseLostException(
                    "market wallet action lease was lost after escrow failure compensation: actionId="
                            + lease.actionId()
            );
        }
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeWalletSuccess(
            MarketWalletAction action,
            MarketWalletActionLease lease,
            UUID walletTxnId,
            Date leaseValidAt
    ) {
        lockOrderFirst(action);
        if (walletActionRepository.lockClaimed(lease, leaseValidAt) == null) {
            return false;
        }

        boolean sagaAdvanced = applyWalletTxnToSaga(action, walletTxnId);
        if (!sagaAdvanced) {
            return walletActionRepository.markRecoveryPending(
                    lease,
                    walletTxnId,
                    SAGA_STATE_NOT_ADVANCED,
                    SAGA_STATE_NOT_ADVANCED_MESSAGE
            ) == 1;
        }

        int updated = walletActionRepository.markSucceeded(
                lease,
                walletTxnId,
                MarketWalletActionResultType.APPLIED
        );
        if (updated != 1) {
            throw new LeaseLostException(
                    "market wallet action lease was lost after saga advance: actionId=" + lease.actionId()
            );
        }
        return true;
    }

    private boolean applyWalletTxnToSaga(MarketWalletAction action, UUID walletTxnId) {
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
        throw new IllegalArgumentException("unsupported market wallet action type: " + action.getActionType());
    }

    private void lockOrderFirst(MarketWalletAction action) {
        if (orderRepository != null) {
            orderRepository.lockById(action.getOrderId());
        }
    }
}
