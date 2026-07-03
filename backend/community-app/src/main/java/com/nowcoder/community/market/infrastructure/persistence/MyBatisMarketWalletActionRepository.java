package com.nowcoder.community.market.infrastructure.persistence;

import com.nowcoder.community.market.domain.model.MarketWalletAction;
import com.nowcoder.community.market.domain.repository.MarketWalletActionRepository;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketWalletActionDataObject;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketWalletActionMapper;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Repository
public class MyBatisMarketWalletActionRepository implements MarketWalletActionRepository {

    private final MarketWalletActionMapper mapper;

    public MyBatisMarketWalletActionRepository(MarketWalletActionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int save(MarketWalletAction action) {
        return mapper.insert(MarketWalletActionDataObject.from(action));
    }

    @Override
    public MarketWalletAction findById(UUID actionId) {
        return mapper.selectById(actionId);
    }

    @Override
    public MarketWalletAction findByRequestId(String requestId) {
        return mapper.selectByRequestId(requestId);
    }

    @Override
    public MarketWalletAction findByOrderAndType(UUID orderId, String actionType) {
        return mapper.selectByOrderAndType(orderId, actionType);
    }

    @Override
    public List<MarketWalletAction> findDue(Date asOf, int limit) {
        return DomainRowAdapter.asDomainList(mapper.selectDue(asOf, limit));
    }

    @Override
    public List<MarketWalletAction> findUnfinishedWithWalletTxn(int limit) {
        return DomainRowAdapter.asDomainList(mapper.selectUnfinishedWithWalletTxn(limit));
    }

    @Override
    public int claimProcessing(UUID actionId, Date leaseUntil) {
        return mapper.claimProcessing(actionId, leaseUntil);
    }

    @Override
    public int markSucceeded(UUID actionId, UUID walletTxnId, String resultType) {
        return mapper.markSucceeded(actionId, walletTxnId, resultType);
    }

    @Override
    public int markCancelled(UUID actionId, String resultType) {
        return mapper.markCancelled(actionId, resultType);
    }

    @Override
    public int cancelPendingEscrow(String requestId, String resultType) {
        return mapper.cancelPendingEscrow(requestId, resultType);
    }

    @Override
    public int markRetrying(UUID actionId, Date nextRetryAt, String lastError) {
        return mapper.markRetrying(actionId, nextRetryAt, lastError);
    }

    @Override
    public int markFailed(UUID actionId, String failureCode, String lastError) {
        return mapper.markFailed(actionId, failureCode, lastError);
    }

    @Override
    public int markRecoveryPending(UUID actionId, UUID walletTxnId, String failureCode, String lastError) {
        return mapper.markRecoveryPending(actionId, walletTxnId, failureCode, lastError);
    }

    @Override
    public int markDead(UUID actionId, String lastError) {
        return mapper.markDead(actionId, lastError);
    }

    @Override
    public int recoverExpiredProcessing(Date asOf) {
        return mapper.recoverExpiredProcessing(asOf);
    }
}
