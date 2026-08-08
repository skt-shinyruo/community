package com.nowcoder.community.market.domain.repository;

import com.nowcoder.community.market.domain.model.MarketWalletAction;
import com.nowcoder.community.market.domain.model.MarketWalletActionClaim;
import com.nowcoder.community.market.domain.model.MarketWalletActionLease;
import com.nowcoder.community.market.domain.model.MarketWalletActionLeaseRecovery;

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

    List<MarketWalletAction> findDue(Date asOf, int maxRetryAttempts, int limit);

    List<MarketWalletAction> findExpiredProcessing(Date asOf, int limit);

    List<MarketWalletAction> findUnfinishedWithWalletTxn(int limit);

    int claimProcessing(MarketWalletActionClaim claim);

    MarketWalletAction findClaimed(MarketWalletActionLease lease);

    MarketWalletAction lockClaimed(MarketWalletActionLease lease, Date leaseValidAt);

    MarketWalletAction lockById(UUID actionId);

    int recordWalletTxn(MarketWalletActionLease lease, UUID walletTxnId, Date leaseValidAt);

    int markSucceeded(MarketWalletActionLease lease, UUID walletTxnId, String resultType);

    int markCancelled(MarketWalletActionLease lease, String resultType);

    int cancelPendingEscrow(String requestId, String resultType);

    int markRetrying(MarketWalletActionLease lease, Date nextRetryAt, String lastError);

    int markFailed(MarketWalletActionLease lease, String failureCode, String lastError);

    int markRecoveryPending(
            MarketWalletActionLease lease,
            UUID walletTxnId,
            String failureCode,
            String lastError
    );

    int markDead(MarketWalletActionLease lease, String lastError);

    int markRecoveredSucceeded(UUID actionId, String expectedStatus, UUID walletTxnId, String resultType);

    int rescheduleFailed(
            UUID actionId,
            String expectedFailureCode,
            int expectedRetryCount,
            Date nextRetryAt,
            int maxRetryAttempts,
            String lastError
    );

    int recoverExpiredProcessing(MarketWalletActionLeaseRecovery recovery);

    int deferWalletTxnRecovery(
            UUID actionId,
            String expectedStatus,
            UUID walletTxnId,
            Date nextRetryAt,
            String lastError
    );

    int deferFailedRecovery(
            UUID actionId,
            String expectedStatus,
            String expectedFailureCode,
            Date nextRetryAt,
            String lastError
    );
}
