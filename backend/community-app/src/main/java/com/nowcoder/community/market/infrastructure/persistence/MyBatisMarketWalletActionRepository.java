package com.nowcoder.community.market.infrastructure.persistence;

import com.nowcoder.community.market.domain.model.MarketWalletAction;
import com.nowcoder.community.market.domain.model.MarketWalletActionLease;
import com.nowcoder.community.market.domain.repository.MarketWalletActionRepository;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketWalletActionDataObject;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketWalletActionMapper;
import org.springframework.dao.DuplicateKeyException;
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
    public CreateResult create(MarketWalletAction action) {
        try {
            return mapper.insert(MarketWalletActionDataObject.from(action)) == 1
                    ? new CreateResult(CreateStatus.CREATED, action)
                    : new CreateResult(CreateStatus.CONFLICT, null);
        } catch (DuplicateKeyException ignored) {
            MarketWalletAction existing = findByRequestId(action.getRequestId());
            return existing == null
                    ? new CreateResult(CreateStatus.CONFLICT, null)
                    : new CreateResult(CreateStatus.ALREADY_EXISTS, existing);
        }
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
    public int claimProcessing(MarketWalletActionLease lease, Date leaseUntil) {
        return mapper.claimProcessing(lease, leaseUntil);
    }

    @Override
    public int markSucceeded(MarketWalletActionLease lease, UUID walletTxnId, String resultType) {
        return mapper.markSucceeded(lease, walletTxnId, resultType);
    }

    @Override
    public int markCancelled(MarketWalletActionLease lease, String resultType) {
        return mapper.markCancelled(lease, resultType);
    }

    @Override
    public int cancelPendingEscrow(String requestId, String resultType) {
        return mapper.cancelPendingEscrow(requestId, resultType);
    }

    @Override
    public int markRetrying(MarketWalletActionLease lease, Date nextRetryAt, String lastError) {
        return mapper.markRetrying(lease, nextRetryAt, lastError);
    }

    @Override
    public int markFailed(MarketWalletActionLease lease, String failureCode, String lastError) {
        return mapper.markFailed(lease, failureCode, lastError);
    }

    @Override
    public int markRecoveryPending(
            MarketWalletActionLease lease,
            UUID walletTxnId,
            String failureCode,
            String lastError
    ) {
        return mapper.markRecoveryPending(lease, walletTxnId, failureCode, lastError);
    }

    @Override
    public int markDead(MarketWalletActionLease lease, String lastError) {
        return mapper.markDead(lease, lastError);
    }

    @Override
    public int markRecoveredSucceeded(UUID actionId, String expectedStatus, UUID walletTxnId, String resultType) {
        return mapper.markRecoveredSucceeded(actionId, expectedStatus, walletTxnId, resultType);
    }

    @Override
    public int rescheduleFailed(UUID actionId, String expectedFailureCode, Date nextRetryAt, String lastError) {
        return mapper.rescheduleFailed(actionId, expectedFailureCode, nextRetryAt, lastError);
    }

    @Override
    public int recoverExpiredProcessing(Date asOf) {
        return mapper.recoverExpiredProcessing(asOf);
    }
}
