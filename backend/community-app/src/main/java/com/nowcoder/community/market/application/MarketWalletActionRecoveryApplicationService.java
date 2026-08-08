package com.nowcoder.community.market.application;

import com.nowcoder.community.market.application.result.MarketWalletActionRecoveryResult;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.model.MarketWalletAction;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import com.nowcoder.community.market.domain.repository.MarketWalletActionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class MarketWalletActionRecoveryApplicationService {

    private static final Logger log = LoggerFactory.getLogger(MarketWalletActionRecoveryApplicationService.class);
    private static final int DEFAULT_RECOVERY_SCAN_LIMIT = 100;

    private final MarketWalletActionRepository walletActionRepository;
    private final MarketOrderRepository orderRepository;
    private final MarketWalletActionRecoveryTransactionOperations transactionOperations;
    private final Clock clock;
    private final int maxRetryAttempts;

    @Autowired
    public MarketWalletActionRecoveryApplicationService(
            MarketWalletActionRepository walletActionRepository,
            MarketOrderRepository orderRepository,
            MarketWalletActionRecoveryTransactionOperations transactionOperations,
            @Value("${market.wallet-action.max-retry-attempts:8}") int maxRetryAttempts
    ) {
        this(
                walletActionRepository,
                orderRepository,
                transactionOperations,
                Clock.systemUTC(),
                maxRetryAttempts
        );
    }

    MarketWalletActionRecoveryApplicationService(
            MarketWalletActionRepository walletActionRepository,
            MarketOrderRepository orderRepository,
            MarketOrderSagaApplicationService sagaService,
            MarketWalletActionCoordinator actionCoordinator,
            Clock clock
    ) {
        this(
                walletActionRepository,
                orderRepository,
                new MarketWalletActionRecoveryTransactionOperations(
                        walletActionRepository,
                        orderRepository,
                        sagaService,
                        actionCoordinator
                ),
                clock,
                MarketWalletActionRetryPolicy.DEFAULT_MAX_RETRY_ATTEMPTS
        );
    }

    MarketWalletActionRecoveryApplicationService(
            MarketWalletActionRepository walletActionRepository,
            MarketOrderRepository orderRepository,
            MarketWalletActionRecoveryTransactionOperations transactionOperations,
            Clock clock
    ) {
        this(
                walletActionRepository,
                orderRepository,
                transactionOperations,
                clock,
                MarketWalletActionRetryPolicy.DEFAULT_MAX_RETRY_ATTEMPTS
        );
    }

    MarketWalletActionRecoveryApplicationService(
            MarketWalletActionRepository walletActionRepository,
            MarketOrderRepository orderRepository,
            MarketWalletActionRecoveryTransactionOperations transactionOperations,
            Clock clock,
            int maxRetryAttempts
    ) {
        this.walletActionRepository = walletActionRepository;
        this.orderRepository = orderRepository;
        this.transactionOperations = transactionOperations;
        this.clock = clock;
        this.maxRetryAttempts = MarketWalletActionRetryPolicy.normalizeMaxRetryAttempts(maxRetryAttempts);
    }

    public MarketWalletActionRecoveryResult reconcileOnce(int limit) {
        if (limit <= 0) {
            return new MarketWalletActionRecoveryResult(0, 0, 0);
        }
        Instant now = clock.instant();
        int recoveredLeases = recoverExpiredProcessingInternal(now, limit);
        int reconciled = 0;
        int skipped = 0;

        List<MarketWalletAction> actionCandidates = walletActionRepository.findUnfinishedWithWalletTxn(limit);
        for (MarketWalletAction action : actionCandidates) {
            try {
                if (transactionOperations.reconcileWalletTxnAction(action.getActionId())) {
                    reconciled++;
                } else {
                    skipped++;
                    deferWalletTxnRecovery(action, now, "wallet recovery made no progress");
                }
            } catch (RuntimeException ex) {
                skipped++;
                deferWalletTxnRecovery(action, now, ex.toString());
                log.warn("market wallet action recovery failed; continuing batch: actionId={}", action.getActionId(), ex);
            }
        }

        int remaining = limit - reconciled;
        if (remaining > 0) {
            for (MarketOrder order : orderRepository.findWalletPendingOrders(remaining)) {
                try {
                    if (transactionOperations.reconcilePendingOrder(
                            order.getOrderId(),
                            Date.from(now),
                            maxRetryAttempts
                    )) {
                        reconciled++;
                    } else {
                        skipped++;
                        deferPendingOrderRecovery(order, now, "pending order recovery made no progress");
                    }
                } catch (RuntimeException ex) {
                    skipped++;
                    deferPendingOrderRecovery(order, now, ex.toString());
                    log.warn("market pending order recovery failed; continuing batch: orderId={}", order.getOrderId(), ex);
                }
            }
        }

        return new MarketWalletActionRecoveryResult(recoveredLeases, reconciled, skipped);
    }

    public int recoverExpiredProcessing(Instant asOf) {
        Objects.requireNonNull(asOf, "asOf must not be null");
        return recoverExpiredProcessingInternal(asOf, DEFAULT_RECOVERY_SCAN_LIMIT);
    }

    private int recoverExpiredProcessingInternal(Instant asOf, int limit) {
        Date recoveredAt = Date.from(asOf);
        int recovered = 0;
        for (MarketWalletAction action : walletActionRepository.findExpiredProcessing(recoveredAt, limit)) {
            try {
                if (transactionOperations.recoverExpiredProcessing(action, recoveredAt, maxRetryAttempts)) {
                    recovered++;
                }
            } catch (RuntimeException ex) {
                log.warn(
                        "market wallet action lease recovery failed; continuing batch: actionId={}",
                        action.getActionId(),
                        ex
                );
            }
        }
        return recovered;
    }

    private void deferWalletTxnRecovery(MarketWalletAction action, Instant now, String lastError) {
        try {
            transactionOperations.deferWalletTxnRecovery(
                    action.getActionId(),
                    Date.from(MarketWalletActionRetryPolicy.nextRetryAt(now, action.getRetryCount())),
                    lastError
            );
        } catch (RuntimeException deferFailure) {
            log.warn(
                    "market wallet action recovery deferral failed: actionId={}",
                    action.getActionId(),
                    deferFailure
            );
        }
    }

    private void deferPendingOrderRecovery(MarketOrder order, Instant now, String lastError) {
        String actionType = order.pendingWalletActionType();
        if (actionType == null) {
            return;
        }
        try {
            transactionOperations.deferPendingOrderRecovery(
                    order.getOrderId(),
                    actionType,
                    Date.from(now),
                    Date.from(MarketWalletActionRetryPolicy.nextRetryAt(now, 0)),
                    lastError
            );
        } catch (RuntimeException deferFailure) {
            log.warn(
                    "market pending order recovery deferral failed: orderId={}",
                    order.getOrderId(),
                    deferFailure
            );
        }
    }
}
