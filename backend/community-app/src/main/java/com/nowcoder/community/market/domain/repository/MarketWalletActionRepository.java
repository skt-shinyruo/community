package com.nowcoder.community.market.domain.repository;

import com.nowcoder.community.market.domain.model.MarketWalletAction;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public interface MarketWalletActionRepository {

    int save(MarketWalletAction action);

    MarketWalletAction findById(UUID actionId);

    MarketWalletAction findByRequestId(String requestId);

    MarketWalletAction findByOrderAndType(UUID orderId, String actionType);

    List<MarketWalletAction> findDue(Date asOf, int limit);

    List<MarketWalletAction> findUnfinishedWithWalletTxn(int limit);

    int claimProcessing(UUID actionId, Date leaseUntil);

    int markSucceeded(UUID actionId, UUID walletTxnId, String resultType);

    int markCancelled(UUID actionId, String resultType);

    int cancelPendingEscrow(String requestId, String resultType);

    int markRetrying(UUID actionId, Date nextRetryAt, String lastError);

    int markFailed(UUID actionId, String failureCode, String lastError);

    int markRecoveryPending(UUID actionId, UUID walletTxnId, String failureCode, String lastError);

    int markDead(UUID actionId, String lastError);

    int recoverExpiredProcessing(Date asOf);
}
