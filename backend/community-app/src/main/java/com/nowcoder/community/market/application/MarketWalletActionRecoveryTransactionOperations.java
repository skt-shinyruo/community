package com.nowcoder.community.market.application;

import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.model.MarketWalletAction;
import com.nowcoder.community.market.domain.model.MarketWalletActionLeaseRecovery;
import com.nowcoder.community.market.domain.model.MarketWalletActionResultType;
import com.nowcoder.community.market.domain.model.MarketWalletActionStatus;
import com.nowcoder.community.market.domain.model.MarketWalletActionType;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import com.nowcoder.community.market.domain.repository.MarketWalletActionRepository;
import com.nowcoder.community.wallet.api.model.WalletErrorCodes;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public class MarketWalletActionRecoveryTransactionOperations {

    private static final Set<String> RECOVERABLE_RELEASE_REFUND_FAILURE_CODES = Set.of(
            String.valueOf(WalletErrorCodes.ACCOUNT_UPDATE_CONFLICT),
            String.valueOf(WalletErrorCodes.ACCOUNT_BALANCE_INSUFFICIENT)
    );

    private final MarketWalletActionRepository walletActionRepository;
    private final MarketOrderRepository orderRepository;
    private final MarketOrderSagaApplicationService sagaService;
    private final MarketWalletActionCoordinator actionCoordinator;
    private final MarketWalletTxnSagaApplier walletTxnSagaApplier;

    public MarketWalletActionRecoveryTransactionOperations(
            MarketWalletActionRepository walletActionRepository,
            MarketOrderRepository orderRepository,
            MarketOrderSagaApplicationService sagaService,
            MarketWalletActionCoordinator actionCoordinator
    ) {
        this.walletActionRepository = Objects.requireNonNull(walletActionRepository, "walletActionRepository must not be null");
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.sagaService = Objects.requireNonNull(sagaService, "sagaService must not be null");
        this.actionCoordinator = Objects.requireNonNull(actionCoordinator, "actionCoordinator must not be null");
        this.walletTxnSagaApplier = new MarketWalletTxnSagaApplier(this.sagaService, this.actionCoordinator);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recoverExpiredProcessing(
            MarketWalletAction candidate,
            Date asOf,
            int maxRetryAttempts
    ) {
        Date nextRetryAt = Date.from(MarketWalletActionRetryPolicy.nextRetryAt(
                asOf.toInstant(),
                candidate.getRetryCount()
        ));
        return walletActionRepository.recoverExpiredProcessing(new MarketWalletActionLeaseRecovery(
                candidate.getActionId(),
                candidate.getLeaseToken(),
                candidate.getProcessingLeaseUntil(),
                candidate.getRetryCount(),
                asOf,
                nextRetryAt,
                maxRetryAttempts,
                "processing lease expired"
        )) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reconcileWalletTxnAction(UUID actionId) {
        MarketWalletAction candidate = walletActionRepository.findById(actionId);
        if (candidate == null) {
            return false;
        }
        orderRepository.lockById(candidate.getOrderId());
        MarketWalletAction action = walletActionRepository.lockById(actionId);
        if (action == null
                || action.getWalletTxnId() == null
                || MarketWalletActionStatus.PROCESSING.equals(action.getStatus())) {
            return false;
        }
        if (walletTxnSagaApplier.apply(action, action.getWalletTxnId()) || sagaAlreadyHasTxn(action)) {
            int updated = walletActionRepository.markRecoveredSucceeded(
                    action.getActionId(),
                    action.getStatus(),
                    action.getWalletTxnId(),
                    MarketWalletActionResultType.APPLIED
            );
            if (updated != 1) {
                throw staleAfterSagaAdvance(action);
            }
            return true;
        }
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reconcilePendingOrder(UUID orderId, Date asOf, int maxRetryAttempts) {
        MarketOrder order = orderRepository.lockById(orderId);
        if (order == null) {
            return false;
        }
        String actionType = order.pendingWalletActionType();
        if (actionType == null) {
            return false;
        }

        MarketWalletAction action = walletActionRepository.findByOrderAndType(order.getOrderId(), actionType);
        if (action == null) {
            return enqueueMissingAction(order, actionType);
        }
        action = walletActionRepository.lockById(action.getActionId());
        if (action == null || !actionType.equals(action.getActionType())) {
            return false;
        }
        if (action.getWalletTxnId() != null) {
            return reconcileWalletTxnActionCurrent(action);
        }
        if (isFailedActionRepairable(action, actionType)) {
            return walletActionRepository.rescheduleFailed(
                    action.getActionId(),
                    action.getFailureCode(),
                    action.getRetryCount(),
                    Date.from(MarketWalletActionRetryPolicy.nextRetryAt(
                            asOf.toInstant(),
                            action.getRetryCount()
                    )),
                    maxRetryAttempts,
                    action.getLastError()
            ) == 1;
        }
        if (order.isEscrowCancelPending()
                && MarketWalletActionType.ESCROW.equals(action.getActionType())
                && MarketWalletActionStatus.CANCELLED.equals(action.getStatus())
                && MarketWalletActionResultType.NOOP.equals(action.getResultType())) {
            sagaService.completeEscrowNoop(order.getOrderId());
            return true;
        }
        return false;
    }

    private boolean reconcileWalletTxnActionCurrent(MarketWalletAction action) {
        if (MarketWalletActionStatus.PROCESSING.equals(action.getStatus())) {
            return false;
        }
        if (walletTxnSagaApplier.apply(action, action.getWalletTxnId()) || sagaAlreadyHasTxn(action)) {
            int updated = walletActionRepository.markRecoveredSucceeded(
                    action.getActionId(),
                    action.getStatus(),
                    action.getWalletTxnId(),
                    MarketWalletActionResultType.APPLIED
            );
            if (updated != 1) {
                throw staleAfterSagaAdvance(action);
            }
            return true;
        }
        return false;
    }

    private boolean isFailedActionRepairable(MarketWalletAction action, String expectedActionType) {
        return action.getWalletTxnId() == null
                && MarketWalletActionStatus.FAILED.equals(action.getStatus())
                && RECOVERABLE_RELEASE_REFUND_FAILURE_CODES.contains(action.getFailureCode())
                && expectedActionType.equals(action.getActionType())
                && (MarketWalletActionType.RELEASE.equals(action.getActionType())
                || MarketWalletActionType.REFUND.equals(action.getActionType()));
    }

    private boolean sagaAlreadyHasTxn(MarketWalletAction action) {
        MarketOrder order = orderRepository.findById(action.getOrderId());
        if (order == null) {
            return false;
        }
        UUID walletTxnId = action.getWalletTxnId();
        if (MarketWalletActionType.ESCROW.equals(action.getActionType())) {
            return walletTxnId.equals(order.getEscrowTxnId());
        }
        if (MarketWalletActionType.RELEASE.equals(action.getActionType())) {
            return walletTxnId.equals(order.getReleaseTxnId());
        }
        if (MarketWalletActionType.REFUND.equals(action.getActionType())) {
            return walletTxnId.equals(order.getRefundTxnId());
        }
        return false;
    }

    private boolean enqueueMissingAction(MarketOrder order, String actionType) {
        if (MarketWalletActionType.ESCROW.equals(actionType)) {
            if (order.isEscrowCancelPending()) {
                sagaService.completeEscrowNoop(order.getOrderId());
                return true;
            }
            actionCoordinator.enqueueEscrow(
                    order.getOrderId(),
                    order.getBuyerUserId(),
                    order.getSellerUserId(),
                    order.getTotalAmount()
            );
            return true;
        }
        if (MarketWalletActionType.RELEASE.equals(actionType)) {
            actionCoordinator.enqueueRelease(
                    order.getOrderId(),
                    order.getSellerUserId(),
                    order.getBuyerUserId(),
                    order.getTotalAmount()
            );
            return true;
        }
        if (MarketWalletActionType.REFUND.equals(actionType)) {
            actionCoordinator.enqueueRefund(
                    order.getOrderId(),
                    order.getBuyerUserId(),
                    order.getSellerUserId(),
                    order.getTotalAmount()
            );
            return true;
        }
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deferWalletTxnRecovery(UUID actionId, Date nextRetryAt, String lastError) {
        MarketWalletAction candidate = walletActionRepository.findById(actionId);
        if (candidate == null || candidate.getWalletTxnId() == null) {
            return false;
        }
        orderRepository.lockById(candidate.getOrderId());
        MarketWalletAction action = walletActionRepository.lockById(actionId);
        if (action == null || action.getWalletTxnId() == null) {
            return false;
        }
        return walletActionRepository.deferWalletTxnRecovery(
                action.getActionId(),
                action.getStatus(),
                action.getWalletTxnId(),
                nextRetryAt,
                truncate(lastError)
        ) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deferPendingOrderRecovery(
            UUID orderId,
            String actionType,
            Date asOf,
            Date nextRetryAt,
            String lastError
    ) {
        MarketOrder order = orderRepository.lockById(orderId);
        if (order == null || !actionType.equals(order.pendingWalletActionType())) {
            return false;
        }
        MarketWalletAction action = walletActionRepository.findByOrderAndType(orderId, actionType);
        if (action != null) {
            action = walletActionRepository.lockById(action.getActionId());
            if (action != null && action.getFailureCode() != null) {
                walletActionRepository.deferFailedRecovery(
                        action.getActionId(),
                        action.getStatus(),
                        action.getFailureCode(),
                        nextRetryAt,
                        truncate(lastError)
                );
            }
        }
        return orderRepository.deferWalletRecovery(orderId, asOf, nextRetryAt) == 1;
    }

    private IllegalStateException staleAfterSagaAdvance(MarketWalletAction action) {
        return new IllegalStateException(
                "market wallet action changed after saga advance: actionId=" + action.getActionId()
        );
    }

    private String truncate(String value) {
        String normalized = value == null || value.isBlank() ? "wallet recovery made no progress" : value;
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }
}
