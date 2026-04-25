package com.nowcoder.community.market.service;

import com.nowcoder.community.market.entity.MarketOrder;
import com.nowcoder.community.market.entity.MarketWalletAction;
import com.nowcoder.community.market.mapper.MarketOrderMapper;
import com.nowcoder.community.market.mapper.MarketWalletActionMapper;
import com.nowcoder.community.market.model.MarketWalletActionResultType;
import com.nowcoder.community.market.model.MarketWalletActionStatus;
import com.nowcoder.community.market.model.MarketWalletActionType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Service
public class MarketWalletActionRecoveryService {

    private static final String STATUS_ESCROW_PENDING = "ESCROW_PENDING";
    private static final String STATUS_ESCROW_CANCEL_PENDING = "ESCROW_CANCEL_PENDING";
    private static final String STATUS_RELEASE_PENDING = "RELEASE_PENDING";
    private static final String STATUS_REFUND_PENDING = "REFUND_PENDING";
    private static final String STATUS_DISPUTE_RELEASE_PENDING = "DISPUTE_RELEASE_PENDING";
    private static final String STATUS_DISPUTE_REFUND_PENDING = "DISPUTE_REFUND_PENDING";

    private final MarketWalletActionMapper actionMapper;
    private final MarketOrderMapper orderMapper;
    private final MarketOrderSagaService sagaService;
    private final MarketWalletActionService actionService;
    private final Clock clock;

    @Autowired
    public MarketWalletActionRecoveryService(MarketWalletActionMapper actionMapper,
                                             MarketOrderMapper orderMapper,
                                             MarketOrderSagaService sagaService,
                                             MarketWalletActionService actionService) {
        this(actionMapper, orderMapper, sagaService, actionService, Clock.systemUTC());
    }

    MarketWalletActionRecoveryService(MarketWalletActionMapper actionMapper,
                                      MarketOrderMapper orderMapper,
                                      MarketOrderSagaService sagaService,
                                      MarketWalletActionService actionService,
                                      Clock clock) {
        this.actionMapper = actionMapper;
        this.orderMapper = orderMapper;
        this.sagaService = sagaService;
        this.actionService = actionService;
        this.clock = clock;
    }

    @Transactional
    public MarketWalletActionRecoveryResult reconcileOnce(int limit) {
        if (limit <= 0) {
            return new MarketWalletActionRecoveryResult(0, 0, 0);
        }
        int recoveredLeases = recoverExpiredProcessing(clock.instant());
        int reconciled = 0;
        int skipped = 0;

        for (MarketWalletAction action : actionMapper.selectUnfinishedWithWalletTxn(limit)) {
            if (reconcileWalletTxnAction(action)) {
                reconciled++;
            } else {
                skipped++;
            }
        }

        int remaining = limit - reconciled - skipped;
        if (remaining > 0) {
            for (MarketOrder order : orderMapper.selectWalletPendingOrders(remaining)) {
                if (reconcilePendingOrder(order)) {
                    reconciled++;
                } else {
                    skipped++;
                }
            }
        }

        return new MarketWalletActionRecoveryResult(recoveredLeases, reconciled, skipped);
    }

    @Transactional
    public int recoverExpiredProcessing(Instant asOf) {
        Objects.requireNonNull(asOf, "asOf must not be null");
        return actionMapper.recoverExpiredProcessing(Date.from(asOf));
    }

    private boolean reconcileWalletTxnAction(MarketWalletAction action) {
        if (action == null || action.getWalletTxnId() == null) {
            return false;
        }
        if (applyWalletTxnToSaga(action) || sagaAlreadyHasTxn(action)) {
            actionMapper.markSucceeded(
                    action.getActionId(),
                    action.getWalletTxnId(),
                    MarketWalletActionResultType.APPLIED
            );
            return true;
        }
        return false;
    }

    private boolean reconcilePendingOrder(MarketOrder order) {
        String actionType = actionTypeFor(order.getStatus());
        if (actionType == null) {
            return false;
        }

        MarketWalletAction action = actionMapper.selectByOrderAndType(order.getOrderId(), actionType);
        if (action == null) {
            return enqueueMissingAction(order, actionType);
        }
        if (action.getWalletTxnId() != null && !MarketWalletActionStatus.SUCCEEDED.equals(action.getStatus())) {
            return reconcileWalletTxnAction(action);
        }
        if (STATUS_ESCROW_CANCEL_PENDING.equals(order.getStatus())
                && MarketWalletActionType.ESCROW.equals(action.getActionType())
                && MarketWalletActionStatus.CANCELLED.equals(action.getStatus())
                && MarketWalletActionResultType.NOOP.equals(action.getResultType())) {
            sagaService.completeEscrowNoop(order.getOrderId());
            return true;
        }
        return false;
    }

    private boolean applyWalletTxnToSaga(MarketWalletAction action) {
        if (MarketWalletActionType.ESCROW.equals(action.getActionType())) {
            if (sagaService.markEscrowSucceeded(action.getOrderId(), action.getWalletTxnId())) {
                return true;
            }
            if (sagaService.markEscrowCancelRefundPending(action.getOrderId(), action.getWalletTxnId())) {
                actionService.enqueueRefund(
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
            return sagaService.markReleaseSucceeded(action.getOrderId(), action.getWalletTxnId());
        }
        if (MarketWalletActionType.REFUND.equals(action.getActionType())) {
            return sagaService.markRefundSucceeded(action.getOrderId(), action.getWalletTxnId());
        }
        return false;
    }

    private boolean sagaAlreadyHasTxn(MarketWalletAction action) {
        MarketOrder order = orderMapper.selectById(action.getOrderId());
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
            if (STATUS_ESCROW_CANCEL_PENDING.equals(order.getStatus())) {
                sagaService.completeEscrowNoop(order.getOrderId());
                return true;
            }
            actionService.enqueueEscrow(
                    order.getOrderId(),
                    order.getBuyerUserId(),
                    order.getSellerUserId(),
                    order.getTotalAmount()
            );
            return true;
        }
        if (MarketWalletActionType.RELEASE.equals(actionType)) {
            actionService.enqueueRelease(
                    order.getOrderId(),
                    order.getSellerUserId(),
                    order.getBuyerUserId(),
                    order.getTotalAmount()
            );
            return true;
        }
        if (MarketWalletActionType.REFUND.equals(actionType)) {
            actionService.enqueueRefund(
                    order.getOrderId(),
                    order.getBuyerUserId(),
                    order.getSellerUserId(),
                    order.getTotalAmount()
            );
            return true;
        }
        return false;
    }

    private String actionTypeFor(String orderStatus) {
        if (STATUS_ESCROW_PENDING.equals(orderStatus) || STATUS_ESCROW_CANCEL_PENDING.equals(orderStatus)) {
            return MarketWalletActionType.ESCROW;
        }
        if (STATUS_RELEASE_PENDING.equals(orderStatus) || STATUS_DISPUTE_RELEASE_PENDING.equals(orderStatus)) {
            return MarketWalletActionType.RELEASE;
        }
        if (STATUS_REFUND_PENDING.equals(orderStatus) || STATUS_DISPUTE_REFUND_PENDING.equals(orderStatus)) {
            return MarketWalletActionType.REFUND;
        }
        return null;
    }
}
