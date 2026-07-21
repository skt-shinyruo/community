package com.nowcoder.community.market.domain.repository;

import com.nowcoder.community.market.domain.model.MarketWalletAction;
import com.nowcoder.community.market.domain.model.MarketWalletActionLease;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public interface MarketWalletActionRepository {

    enum CreateStatus {
        CREATED,
        ALREADY_EXISTS,
        CONFLICT
    }

    record CreateResult(CreateStatus status, MarketWalletAction aggregate) {
    }

    CreateResult create(MarketWalletAction action);

    MarketWalletAction findById(UUID actionId);

    MarketWalletAction findByRequestId(String requestId);

    MarketWalletAction findByOrderAndType(UUID orderId, String actionType);

    List<MarketWalletAction> findDue(Date asOf, int limit);

    List<MarketWalletAction> findUnfinishedWithWalletTxn(int limit);

    int claimProcessing(UUID actionId, Date leaseUntil);

    int claimProcessing(MarketWalletActionLease lease, Date leaseUntil);

    int markSucceeded(UUID actionId, UUID walletTxnId, String resultType);

    int markSucceeded(MarketWalletActionLease lease, UUID walletTxnId, String resultType);

    int markCancelled(UUID actionId, String resultType);

    int markCancelled(MarketWalletActionLease lease, String resultType);

    int cancelPendingEscrow(String requestId, String resultType);

    int markRetrying(UUID actionId, Date nextRetryAt, String lastError);

    int markRetrying(MarketWalletActionLease lease, Date nextRetryAt, String lastError);

    int markFailed(UUID actionId, String failureCode, String lastError);

    int markFailed(MarketWalletActionLease lease, String failureCode, String lastError);

    int markRecoveryPending(UUID actionId, UUID walletTxnId, String failureCode, String lastError);

    int markRecoveryPending(
            MarketWalletActionLease lease,
            UUID walletTxnId,
            String failureCode,
            String lastError
    );

    int markDead(UUID actionId, String lastError);

    int markDead(MarketWalletActionLease lease, String lastError);

    int markRecoveredSucceeded(UUID actionId, String expectedStatus, UUID walletTxnId, String resultType);

    int rescheduleFailed(UUID actionId, String expectedFailureCode, Date nextRetryAt, String lastError);

    int recoverExpiredProcessing(Date asOf);
}
