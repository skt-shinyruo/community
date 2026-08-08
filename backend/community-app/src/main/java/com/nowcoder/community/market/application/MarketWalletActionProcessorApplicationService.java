package com.nowcoder.community.market.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.market.domain.model.MarketWalletAction;
import com.nowcoder.community.market.domain.model.MarketWalletActionClaim;
import com.nowcoder.community.market.domain.model.MarketWalletActionLease;
import com.nowcoder.community.market.domain.repository.MarketWalletActionRepository;
import com.nowcoder.community.market.domain.model.MarketWalletActionStatus;
import com.nowcoder.community.market.domain.model.MarketWalletActionType;
import com.nowcoder.community.wallet.api.action.WalletMarketActionApi;
import com.nowcoder.community.wallet.api.model.WalletErrorCodes;
import com.nowcoder.community.wallet.api.model.WalletMarketTxnView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class MarketWalletActionProcessorApplicationService {

    private static final Logger log = LoggerFactory.getLogger(MarketWalletActionProcessorApplicationService.class);
    private static final Duration DEFAULT_PROCESSING_LEASE = Duration.ofSeconds(60);
    private static final int MAX_LAST_ERROR_LENGTH = 255;
    private static final String SAGA_COMPLETION_FAILED = "SAGA_COMPLETION_FAILED";
    private static final Set<Integer> RECOVERABLE_RELEASE_REFUND_WALLET_ERROR_CODES = Set.of(
            WalletErrorCodes.ACCOUNT_UPDATE_CONFLICT,
            WalletErrorCodes.ACCOUNT_BALANCE_INSUFFICIENT
    );

    private final MarketWalletActionRepository walletActionRepository;
    private final WalletMarketActionApi walletApi;
    private final MarketWalletActionProcessorTransactionOperations transactionOperations;
    private final Clock clock;
    private final Duration processingLease;
    private final int maxRetryAttempts;

    @Autowired
    public MarketWalletActionProcessorApplicationService(MarketWalletActionRepository walletActionRepository,
                                       WalletMarketActionApi walletApi,
                                       MarketWalletActionProcessorTransactionOperations transactionOperations,
                                       @Value("${market.wallet-action.processing-lease:60s}") Duration processingLease,
                                       @Value("${market.wallet-action.max-retry-attempts:8}") int maxRetryAttempts) {
        this(
                walletActionRepository,
                walletApi,
                transactionOperations,
                Clock.systemUTC(),
                processingLease,
                maxRetryAttempts
        );
    }

    MarketWalletActionProcessorApplicationService(MarketWalletActionRepository walletActionRepository,
                                WalletMarketActionApi walletApi,
                                MarketOrderSagaApplicationService sagaService,
                                MarketWalletActionCoordinator actionCoordinator,
                                Clock clock) {
        this(
                walletActionRepository,
                walletApi,
                new MarketWalletActionProcessorTransactionOperations(
                        walletActionRepository,
                        sagaService,
                        actionCoordinator
                ),
                clock,
                DEFAULT_PROCESSING_LEASE,
                MarketWalletActionRetryPolicy.DEFAULT_MAX_RETRY_ATTEMPTS
        );
    }

    MarketWalletActionProcessorApplicationService(MarketWalletActionRepository walletActionRepository,
                                WalletMarketActionApi walletApi,
                                MarketOrderSagaApplicationService sagaService,
                                MarketWalletActionCoordinator actionCoordinator,
                                Clock clock,
                                Duration processingLease) {
        this(
                walletActionRepository,
                walletApi,
                new MarketWalletActionProcessorTransactionOperations(
                        walletActionRepository,
                        sagaService,
                        actionCoordinator
                ),
                clock,
                processingLease,
                MarketWalletActionRetryPolicy.DEFAULT_MAX_RETRY_ATTEMPTS
        );
    }

    MarketWalletActionProcessorApplicationService(MarketWalletActionRepository walletActionRepository,
                                WalletMarketActionApi walletApi,
                                MarketOrderSagaApplicationService sagaService,
                                MarketWalletActionCoordinator actionCoordinator,
                                Clock clock,
                                Duration processingLease,
                                int maxRetryAttempts) {
        this(
                walletActionRepository,
                walletApi,
                new MarketWalletActionProcessorTransactionOperations(
                        walletActionRepository,
                        sagaService,
                        actionCoordinator
                ),
                clock,
                processingLease,
                maxRetryAttempts
        );
    }

    private MarketWalletActionProcessorApplicationService(
            MarketWalletActionRepository walletActionRepository,
            WalletMarketActionApi walletApi,
            MarketWalletActionProcessorTransactionOperations transactionOperations,
            Clock clock,
            Duration processingLease,
            int maxRetryAttempts
    ) {
        this.walletActionRepository = walletActionRepository;
        this.walletApi = walletApi;
        this.transactionOperations = transactionOperations;
        this.clock = clock;
        this.processingLease = normalizeProcessingLease(processingLease);
        this.maxRetryAttempts = MarketWalletActionRetryPolicy.normalizeMaxRetryAttempts(maxRetryAttempts);
    }

    public int processDue(int limit) {
        if (limit <= 0) {
            return 0;
        }
        List<MarketWalletAction> actions = walletActionRepository.findDue(
                Date.from(clock.instant()),
                maxRetryAttempts,
                limit
        );
        int processed = 0;
        for (MarketWalletAction action : actions) {
            if (processOne(action)) {
                processed++;
            }
        }
        return processed;
    }

    public boolean processOne(MarketWalletAction action) {
        Instant claimedAt = clock.instant();
        MarketWalletActionLease lease = new MarketWalletActionLease(action.getActionId(), UUID.randomUUID());
        MarketWalletActionClaim claim = new MarketWalletActionClaim(
                lease,
                action.getStatus(),
                action.getRetryCount(),
                Date.from(claimedAt),
                Date.from(claimedAt.plus(processingLease)),
                maxRetryAttempts
        );
        int claimed = walletActionRepository.claimProcessing(claim);
        if (claimed != 1) {
            return false;
        }
        MarketWalletAction claimedAction = walletActionRepository.findClaimed(lease);
        if (claimedAction == null) {
            log.warn("[market-wallet-action] claimed row unavailable actionId={}", lease.actionId());
            return false;
        }
        try {
            return route(claimedAction, lease, claim.expectedStatus());
        } catch (RuntimeException ex) {
            return handleFailure(claimedAction, lease, ex);
        }
    }

    private static Duration normalizeProcessingLease(Duration processingLease) {
        return processingLease == null || processingLease.isZero() || processingLease.isNegative()
                ? DEFAULT_PROCESSING_LEASE
                : processingLease;
    }

    private boolean route(
            MarketWalletAction action,
            MarketWalletActionLease lease,
            String claimedFromStatus
    ) {
        if (MarketWalletActionType.ESCROW.equals(action.getActionType())) {
            return processEscrow(action, lease, claimedFromStatus);
        }
        if (MarketWalletActionType.RELEASE.equals(action.getActionType())) {
            WalletMarketTxnView result = walletApi.releaseOrder(
                    action.getRequestId(),
                    action.getActorUserId(),
                    action.getAmount(),
                    action.getWalletBizId()
            );
            return completeWalletSuccess(action, lease, result);
        }
        if (MarketWalletActionType.REFUND.equals(action.getActionType())) {
            WalletMarketTxnView result = walletApi.refundOrder(
                    action.getRequestId(),
                    action.getActorUserId(),
                    action.getAmount(),
                    action.getWalletBizId()
            );
            return completeWalletSuccess(action, lease, result);
        }
        throw new IllegalArgumentException("unsupported market wallet action type: " + action.getActionType());
    }

    private boolean processEscrow(
            MarketWalletAction action,
            MarketWalletActionLease lease,
            String claimedFromStatus
    ) {
        if (MarketWalletActionStatus.PENDING.equals(claimedFromStatus)
                && !transactionOperations.canApplyEscrow(action.getOrderId())) {
            return transactionOperations.completeEscrowNoop(action, lease, Date.from(clock.instant()));
        }
        WalletMarketTxnView result = walletApi.escrowOrder(
                action.getRequestId(),
                action.getActorUserId(),
                action.getAmount(),
                action.getWalletBizId()
        );
        return completeWalletSuccess(action, lease, result);
    }

    private boolean completeWalletSuccess(
            MarketWalletAction action,
            MarketWalletActionLease lease,
            WalletMarketTxnView result
    ) {
        if (!transactionOperations.recordWalletTxn(lease, result.txnId(), Date.from(clock.instant()))) {
            log.warn("[market-wallet-action] lease lost before wallet result persistence actionId={}", lease.actionId());
            return false;
        }
        action.setWalletTxnId(result.txnId());
        return transactionOperations.completeWalletSuccess(
                action,
                lease,
                result.txnId(),
                Date.from(clock.instant())
        );
    }

    private boolean handleFailure(
            MarketWalletAction action,
            MarketWalletActionLease lease,
            RuntimeException ex
    ) {
        if (ex instanceof MarketWalletActionProcessorTransactionOperations.LeaseLostException) {
            return false;
        }
        if (action.getWalletTxnId() != null) {
            return ownsTransition(
                    lease,
                    "recovery-pending",
                    walletActionRepository.markRecoveryPending(
                            lease,
                            action.getWalletTxnId(),
                            SAGA_COMPLETION_FAILED,
                            lastError(ex)
                    )
            );
        }
        if (isRetryable(action, ex)) {
            if (action.getRetryCount() + 1 >= maxRetryAttempts) {
                return ownsTransition(
                        lease,
                        "dead",
                        walletActionRepository.markDead(lease, lastError(ex))
                );
            }
            ownsTransition(
                    lease,
                    "retrying",
                    walletActionRepository.markRetrying(
                            lease,
                            Date.from(nextRetryAt(action)),
                            lastError(ex)
                    )
            );
            return false;
        }
        if (MarketWalletActionType.ESCROW.equals(action.getActionType())) {
            return transactionOperations.completeEscrowTerminalFailure(
                    action,
                    lease,
                    failureCode(ex),
                    lastError(ex),
                    Date.from(clock.instant())
            );
        }
        return ownsTransition(
                lease,
                "failed",
                walletActionRepository.markFailed(lease, failureCode(ex), lastError(ex))
        );
    }

    private boolean ownsTransition(MarketWalletActionLease lease, String transition, int updated) {
        if (updated == 1) {
            return true;
        }
        log.warn(
                "[market-wallet-action] lease lost actionId={} transition={}",
                lease.actionId(),
                transition
        );
        return false;
    }

    private boolean isRetryable(MarketWalletAction action, RuntimeException ex) {
        if (!(ex instanceof BusinessException businessException)) {
            return true;
        }
        ErrorCode errorCode = businessException.getErrorCode();
        if (errorCode != null && errorCode.getCode() == WalletErrorCodes.ACCOUNT_UPDATE_CONFLICT) {
            return true;
        }
        if (MarketWalletActionType.ESCROW.equals(action.getActionType())) {
            return false;
        }
        return errorCode != null
                && RECOVERABLE_RELEASE_REFUND_WALLET_ERROR_CODES.contains(errorCode.getCode());
    }

    private Instant nextRetryAt(MarketWalletAction action) {
        return MarketWalletActionRetryPolicy.nextRetryAt(clock.instant(), action.getRetryCount());
    }

    private String failureCode(RuntimeException ex) {
        if (ex instanceof BusinessException businessException) {
            ErrorCode errorCode = businessException.getErrorCode();
            return errorCode == null ? null : String.valueOf(errorCode.getCode());
        }
        return ex.getClass().getSimpleName();
    }

    private String lastError(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getName();
        }
        return message.length() <= MAX_LAST_ERROR_LENGTH ? message : message.substring(0, MAX_LAST_ERROR_LENGTH);
    }
}
