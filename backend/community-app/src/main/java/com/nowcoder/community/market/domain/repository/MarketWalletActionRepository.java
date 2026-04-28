package com.nowcoder.community.market.domain.repository;

import com.nowcoder.community.market.domain.model.MarketWalletAction;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public interface MarketWalletActionRepository {

    int insert(MarketWalletAction action);

    MarketWalletAction selectById(UUID actionId);

    MarketWalletAction selectByRequestId(String requestId);

    MarketWalletAction selectByOrderAndType(UUID orderId, String actionType);

    List<MarketWalletAction> selectDue(Date asOf, int limit);

    List<MarketWalletAction> selectUnfinishedWithWalletTxn(int limit);

    int claimProcessing(UUID actionId, Date leaseUntil);

    int markSucceeded(UUID actionId, UUID walletTxnId, String resultType);

    int markCancelled(UUID actionId, String resultType);

    int cancelPendingEscrow(String requestId, String resultType);

    int markRetrying(UUID actionId, Date nextRetryAt, String lastError);

    int markFailed(UUID actionId, String failureCode, String lastError);

    int markDead(UUID actionId, String lastError);

    int recoverExpiredProcessing(Date asOf);
}
