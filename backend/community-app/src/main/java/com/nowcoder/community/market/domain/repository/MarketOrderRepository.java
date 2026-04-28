package com.nowcoder.community.market.domain.repository;

import com.nowcoder.community.market.domain.model.MarketOrder;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public interface MarketOrderRepository {

    int insert(MarketOrder order);

    MarketOrder selectById(UUID orderId);

    MarketOrder selectByIdForUpdate(UUID orderId);

    MarketOrder selectByRequestId(String requestId);

    MarketOrder selectByRequestIdForUpdate(String requestId);

    MarketOrder selectByBuyerUserIdAndRequestId(UUID buyerUserId, String requestId);

    MarketOrder selectByBuyerUserIdAndRequestIdForUpdate(UUID buyerUserId, String requestId);

    List<MarketOrder> selectByBuyerUserId(UUID buyerUserId);

    List<MarketOrder> selectBySellerUserId(UUID sellerUserId);

    int markDelivered(UUID orderId, Date autoConfirmAt);

    int markShipped(UUID orderId, Date autoConfirmAt);

    int markEscrowSucceeded(UUID orderId, UUID escrowTxnId);

    int markEscrowFailed(UUID orderId);

    int markReleasePending(UUID orderId);

    int markReleaseSucceeded(UUID orderId, UUID releaseTxnId);

    int markRefundPending(UUID orderId);

    int markEscrowCancelPending(UUID orderId);

    int markEscrowCancelRefundPending(UUID orderId, UUID escrowTxnId);

    int markCancelledNoRefund(UUID orderId);

    int markCancelledWithRefund(UUID orderId, UUID refundTxnId);

    int markDisputeRefundPending(UUID orderId);

    int markDisputeReleasePending(UUID orderId);

    int markDisputeRefundSucceeded(UUID orderId, UUID refundTxnId);

    int markCompleted(UUID orderId, UUID releaseTxnId);

    int markCancelled(UUID orderId, UUID refundTxnId);

    int markDisputed(UUID orderId);

    int markRefunded(UUID orderId, UUID refundTxnId);

    int updateStatus(UUID orderId, String status);

    List<MarketOrder> selectDueForAutoConfirm(Date asOf);

    List<MarketOrder> selectWalletPendingOrders(int limit);
}
