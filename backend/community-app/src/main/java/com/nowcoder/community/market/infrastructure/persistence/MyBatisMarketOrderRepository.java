package com.nowcoder.community.market.infrastructure.persistence;

import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketOrderDataObject;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketOrderMapper;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Repository
public class MyBatisMarketOrderRepository implements MarketOrderRepository {

    private final MarketOrderMapper mapper;

    public MyBatisMarketOrderRepository(MarketOrderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int insert(MarketOrder order) {
        return mapper.insert(MarketOrderDataObject.from(order));
    }

    @Override
    public MarketOrder selectById(UUID orderId) {
        return mapper.selectById(orderId);
    }

    @Override
    public MarketOrder selectByIdForUpdate(UUID orderId) {
        return mapper.selectByIdForUpdate(orderId);
    }

    @Override
    public MarketOrder selectByRequestId(String requestId) {
        return mapper.selectByRequestId(requestId);
    }

    @Override
    public MarketOrder selectByRequestIdForUpdate(String requestId) {
        return mapper.selectByRequestIdForUpdate(requestId);
    }

    @Override
    public MarketOrder selectByBuyerUserIdAndRequestId(UUID buyerUserId, String requestId) {
        return mapper.selectByBuyerUserIdAndRequestId(buyerUserId, requestId);
    }

    @Override
    public MarketOrder selectByBuyerUserIdAndRequestIdForUpdate(UUID buyerUserId, String requestId) {
        return mapper.selectByBuyerUserIdAndRequestIdForUpdate(buyerUserId, requestId);
    }

    @Override
    public List<MarketOrder> selectByBuyerUserId(UUID buyerUserId) {
        return DomainRowAdapter.asDomainList(mapper.selectByBuyerUserId(buyerUserId));
    }

    @Override
    public List<MarketOrder> selectBySellerUserId(UUID sellerUserId) {
        return DomainRowAdapter.asDomainList(mapper.selectBySellerUserId(sellerUserId));
    }

    @Override
    public int markDelivered(UUID orderId, Date autoConfirmAt) {
        return mapper.markDelivered(orderId, autoConfirmAt);
    }

    @Override
    public int markShipped(UUID orderId, Date autoConfirmAt) {
        return mapper.markShipped(orderId, autoConfirmAt);
    }

    @Override
    public int markEscrowSucceeded(UUID orderId, UUID escrowTxnId) {
        return mapper.markEscrowSucceeded(orderId, escrowTxnId);
    }

    @Override
    public int markEscrowFailed(UUID orderId) {
        return mapper.markEscrowFailed(orderId);
    }

    @Override
    public int markReleasePending(UUID orderId) {
        return mapper.markReleasePending(orderId);
    }

    @Override
    public int markReleaseSucceeded(UUID orderId, UUID releaseTxnId) {
        return mapper.markReleaseSucceeded(orderId, releaseTxnId);
    }

    @Override
    public int markRefundPending(UUID orderId) {
        return mapper.markRefundPending(orderId);
    }

    @Override
    public int markEscrowCancelPending(UUID orderId) {
        return mapper.markEscrowCancelPending(orderId);
    }

    @Override
    public int markEscrowCancelRefundPending(UUID orderId, UUID escrowTxnId) {
        return mapper.markEscrowCancelRefundPending(orderId, escrowTxnId);
    }

    @Override
    public int markCancelledNoRefund(UUID orderId) {
        return mapper.markCancelledNoRefund(orderId);
    }

    @Override
    public int markCancelledWithRefund(UUID orderId, UUID refundTxnId) {
        return mapper.markCancelledWithRefund(orderId, refundTxnId);
    }

    @Override
    public int markDisputeRefundPending(UUID orderId) {
        return mapper.markDisputeRefundPending(orderId);
    }

    @Override
    public int markDisputeReleasePending(UUID orderId) {
        return mapper.markDisputeReleasePending(orderId);
    }

    @Override
    public int markDisputeRefundSucceeded(UUID orderId, UUID refundTxnId) {
        return mapper.markDisputeRefundSucceeded(orderId, refundTxnId);
    }

    @Override
    public int markCompleted(UUID orderId, UUID releaseTxnId) {
        return mapper.markCompleted(orderId, releaseTxnId);
    }

    @Override
    public int markCancelled(UUID orderId, UUID refundTxnId) {
        return mapper.markCancelled(orderId, refundTxnId);
    }

    @Override
    public int markDisputed(UUID orderId) {
        return mapper.markDisputed(orderId);
    }

    @Override
    public int markRefunded(UUID orderId, UUID refundTxnId) {
        return mapper.markRefunded(orderId, refundTxnId);
    }

    @Override
    public int updateStatus(UUID orderId, String status) {
        return mapper.updateStatus(orderId, status);
    }

    @Override
    public List<MarketOrder> selectDueForAutoConfirm(Date asOf) {
        return DomainRowAdapter.asDomainList(mapper.selectDueForAutoConfirm(asOf));
    }

    @Override
    public List<MarketOrder> selectWalletPendingOrders(int limit) {
        return DomainRowAdapter.asDomainList(mapper.selectWalletPendingOrders(limit));
    }
}
