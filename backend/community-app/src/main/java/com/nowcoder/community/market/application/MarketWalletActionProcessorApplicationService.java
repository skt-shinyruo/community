package com.nowcoder.community.market.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.market.domain.model.MarketWalletAction;
import com.nowcoder.community.market.domain.repository.MarketWalletActionRepository;
import com.nowcoder.community.market.domain.model.MarketWalletActionResultType;
import com.nowcoder.community.market.domain.model.MarketWalletActionType;
import com.nowcoder.community.wallet.api.action.WalletMarketActionApi;
import com.nowcoder.community.wallet.api.model.WalletErrorCodes;
import com.nowcoder.community.wallet.api.model.WalletMarketTxnView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Service
public class MarketWalletActionProcessorApplicationService {

    private static final Duration DEFAULT_PROCESSING_LEASE = Duration.ofSeconds(60);
    private static final int MAX_LAST_ERROR_LENGTH = 255;
    private static final Set<Integer> RECOVERABLE_RELEASE_REFUND_WALLET_ERROR_CODES = Set.of(
            WalletErrorCodes.ACCOUNT_UPDATE_CONFLICT,
            WalletErrorCodes.ACCOUNT_BALANCE_INSUFFICIENT
    );

    private final MarketWalletActionRepository walletActionRepository;
    private final WalletMarketActionApi walletApi;
    private final MarketOrderSagaApplicationService sagaService;
    private final MarketWalletActionApplicationService actionService;
    private final Clock clock;
    private final Duration processingLease;

    @Autowired
    public MarketWalletActionProcessorApplicationService(MarketWalletActionRepository walletActionRepository,
                                       WalletMarketActionApi walletApi,
                                       MarketOrderSagaApplicationService sagaService,
                                       MarketWalletActionApplicationService actionService,
                                       @Value("${market.wallet-action.processing-lease:60s}") Duration processingLease) {
        this(walletActionRepository, walletApi, sagaService, actionService, Clock.systemUTC(), processingLease);
    }

    MarketWalletActionProcessorApplicationService(MarketWalletActionRepository walletActionRepository,
                                WalletMarketActionApi walletApi,
                                MarketOrderSagaApplicationService sagaService,
                                MarketWalletActionApplicationService actionService,
                                Clock clock) {
        this(walletActionRepository, walletApi, sagaService, actionService, clock, DEFAULT_PROCESSING_LEASE);
    }

    MarketWalletActionProcessorApplicationService(MarketWalletActionRepository walletActionRepository,
                                WalletMarketActionApi walletApi,
                                MarketOrderSagaApplicationService sagaService,
                                MarketWalletActionApplicationService actionService,
                                Clock clock,
                                Duration processingLease) {
        this.walletActionRepository = walletActionRepository;
        this.walletApi = walletApi;
        this.sagaService = sagaService;
        this.actionService = actionService;
        this.clock = clock;
        this.processingLease = normalizeProcessingLease(processingLease);
    }

    public int processDue(int limit) {
        if (limit <= 0) {
            return 0;
        }
        List<MarketWalletAction> actions = walletActionRepository.findDue(Date.from(clock.instant()), limit);
        int processed = 0;
        for (MarketWalletAction action : actions) {
            if (processOne(action)) {
                processed++;
            }
        }
        return processed;
    }

    public boolean processOne(MarketWalletAction action) {
        Date leaseUntil = Date.from(clock.instant().plus(processingLease));
        int claimed = walletActionRepository.claimProcessing(action.getActionId(), leaseUntil);
        if (claimed != 1) {
            return false;
        }
        try {
            route(action);
            return true;
        } catch (RuntimeException ex) {
            return handleFailure(action, ex);
        }
    }

    private static Duration normalizeProcessingLease(Duration processingLease) {
        return processingLease == null || processingLease.isZero() || processingLease.isNegative()
                ? DEFAULT_PROCESSING_LEASE
                : processingLease;
    }

    private void route(MarketWalletAction action) {
        if (MarketWalletActionType.ESCROW.equals(action.getActionType())) {
            processEscrow(action);
            return;
        }
        if (MarketWalletActionType.RELEASE.equals(action.getActionType())) {
            WalletMarketTxnView result = walletApi.releaseOrder(
                    action.getRequestId(),
                    action.getActorUserId(),
                    action.getAmount(),
                    action.getWalletBizId()
            );
            sagaService.markReleaseSucceeded(action.getOrderId(), result.txnId());
            walletActionRepository.markSucceeded(action.getActionId(), result.txnId(), MarketWalletActionResultType.APPLIED);
            return;
        }
        if (MarketWalletActionType.REFUND.equals(action.getActionType())) {
            WalletMarketTxnView result = walletApi.refundOrder(
                    action.getRequestId(),
                    action.getActorUserId(),
                    action.getAmount(),
                    action.getWalletBizId()
            );
            sagaService.markRefundSucceeded(action.getOrderId(), result.txnId());
            walletActionRepository.markSucceeded(action.getActionId(), result.txnId(), MarketWalletActionResultType.APPLIED);
            return;
        }
        throw new IllegalArgumentException("unsupported market wallet action type: " + action.getActionType());
    }

    private void processEscrow(MarketWalletAction action) {
        if (!sagaService.canApplyEscrow(action.getOrderId())) {
            walletActionRepository.markCancelled(action.getActionId(), MarketWalletActionResultType.NOOP);
            sagaService.completeEscrowNoop(action.getOrderId());
            return;
        }
        WalletMarketTxnView result = walletApi.escrowOrder(
                action.getRequestId(),
                action.getActorUserId(),
                action.getAmount(),
                action.getWalletBizId()
        );
        if (!sagaService.markEscrowSucceeded(action.getOrderId(), result.txnId())
                && sagaService.markEscrowCancelRefundPending(action.getOrderId(), result.txnId())) {
            actionService.enqueueRefund(
                    action.getOrderId(),
                    action.getActorUserId(),
                    action.getCounterpartyUserId(),
                    action.getAmount()
            );
        }
        walletActionRepository.markSucceeded(action.getActionId(), result.txnId(), MarketWalletActionResultType.APPLIED);
    }

    private boolean handleFailure(MarketWalletAction action, RuntimeException ex) {
        if (isRetryable(action, ex)) {
            walletActionRepository.markRetrying(
                    action.getActionId(),
                    Date.from(nextRetryAt(action)),
                    lastError(ex)
            );
            return false;
        }
        if (MarketWalletActionType.ESCROW.equals(action.getActionType())) {
            sagaService.markEscrowTerminalFailed(action.getOrderId(), ex.getMessage());
        }
        walletActionRepository.markFailed(action.getActionId(), failureCode(ex), lastError(ex));
        return true;
    }

    private boolean isRetryable(MarketWalletAction action, RuntimeException ex) {
        if (!(ex instanceof BusinessException businessException)) {
            return true;
        }
        if (MarketWalletActionType.ESCROW.equals(action.getActionType())) {
            return false;
        }
        ErrorCode errorCode = businessException.getErrorCode();
        return errorCode != null
                && RECOVERABLE_RELEASE_REFUND_WALLET_ERROR_CODES.contains(errorCode.getCode());
    }

    private Instant nextRetryAt(MarketWalletAction action) {
        long delaySeconds = Math.min(300L, 5L * (1L << Math.min(action.getRetryCount(), 6)));
        return clock.instant().plus(delaySeconds, ChronoUnit.SECONDS);
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
