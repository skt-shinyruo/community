package com.nowcoder.community.market.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.market.entity.MarketWalletAction;
import com.nowcoder.community.market.mapper.MarketWalletActionMapper;
import com.nowcoder.community.market.model.MarketWalletActionResultType;
import com.nowcoder.community.market.model.MarketWalletActionType;
import com.nowcoder.community.wallet.api.action.WalletMarketActionApi;
import com.nowcoder.community.wallet.api.model.WalletMarketTxnView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

@Service
public class MarketWalletActionProcessor {

    private static final int PROCESSING_LEASE_SECONDS = 60;
    private static final int MAX_LAST_ERROR_LENGTH = 255;

    private final MarketWalletActionMapper actionMapper;
    private final WalletMarketActionApi walletApi;
    private final MarketOrderSagaService sagaService;
    private final Clock clock;

    @Autowired
    public MarketWalletActionProcessor(MarketWalletActionMapper actionMapper,
                                       WalletMarketActionApi walletApi,
                                       MarketOrderSagaService sagaService) {
        this(actionMapper, walletApi, sagaService, Clock.systemUTC());
    }

    MarketWalletActionProcessor(MarketWalletActionMapper actionMapper,
                                WalletMarketActionApi walletApi,
                                MarketOrderSagaService sagaService,
                                Clock clock) {
        this.actionMapper = actionMapper;
        this.walletApi = walletApi;
        this.sagaService = sagaService;
        this.clock = clock;
    }

    public int processDue(int limit) {
        if (limit <= 0) {
            return 0;
        }
        List<MarketWalletAction> actions = actionMapper.selectDue(Date.from(clock.instant()), limit);
        int processed = 0;
        for (MarketWalletAction action : actions) {
            if (processOne(action)) {
                processed++;
            }
        }
        return processed;
    }

    public boolean processOne(MarketWalletAction action) {
        Date leaseUntil = Date.from(clock.instant().plus(PROCESSING_LEASE_SECONDS, ChronoUnit.SECONDS));
        int claimed = actionMapper.claimProcessing(action.getActionId(), leaseUntil);
        if (claimed != 1) {
            return false;
        }
        try {
            route(action);
            return true;
        } catch (RuntimeException ex) {
            handleFailure(action, ex);
            return false;
        }
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
            actionMapper.markSucceeded(action.getActionId(), result.txnId(), MarketWalletActionResultType.APPLIED);
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
            actionMapper.markSucceeded(action.getActionId(), result.txnId(), MarketWalletActionResultType.APPLIED);
            return;
        }
        throw new IllegalArgumentException("unsupported market wallet action type: " + action.getActionType());
    }

    private void processEscrow(MarketWalletAction action) {
        if (!sagaService.canApplyEscrow(action.getOrderId())) {
            actionMapper.markCancelled(action.getActionId(), MarketWalletActionResultType.NOOP);
            sagaService.completeEscrowNoop(action.getOrderId());
            return;
        }
        WalletMarketTxnView result = walletApi.escrowOrder(
                action.getRequestId(),
                action.getActorUserId(),
                action.getAmount(),
                action.getWalletBizId()
        );
        sagaService.markEscrowSucceeded(action.getOrderId(), result.txnId());
        actionMapper.markSucceeded(action.getActionId(), result.txnId(), MarketWalletActionResultType.APPLIED);
    }

    private void handleFailure(MarketWalletAction action, RuntimeException ex) {
        if (isRetryable(ex)) {
            actionMapper.markRetrying(
                    action.getActionId(),
                    Date.from(nextRetryAt(action)),
                    lastError(ex)
            );
            return;
        }
        if (MarketWalletActionType.ESCROW.equals(action.getActionType())) {
            sagaService.markEscrowTerminalFailed(action.getOrderId(), ex.getMessage());
        }
        actionMapper.markFailed(action.getActionId(), failureCode(ex), lastError(ex));
    }

    private boolean isRetryable(RuntimeException ex) {
        return !(ex instanceof BusinessException);
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
