package com.nowcoder.community.market.mapper;

import com.nowcoder.community.market.entity.MarketOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface MarketOrderMapper {

    int insert(MarketOrder order);

    MarketOrder selectById(@Param("orderId") UUID orderId);

    MarketOrder selectByIdForUpdate(@Param("orderId") UUID orderId);

    MarketOrder selectByRequestId(@Param("requestId") String requestId);

    MarketOrder selectByRequestIdForUpdate(@Param("requestId") String requestId);

    List<MarketOrder> selectByBuyerUserId(@Param("buyerUserId") UUID buyerUserId);

    List<MarketOrder> selectBySellerUserId(@Param("sellerUserId") UUID sellerUserId);

    int markDelivered(@Param("orderId") UUID orderId, @Param("autoConfirmAt") Date autoConfirmAt);

    int markShipped(@Param("orderId") UUID orderId, @Param("autoConfirmAt") Date autoConfirmAt);

    int markEscrowSucceeded(@Param("orderId") UUID orderId, @Param("escrowTxnId") UUID escrowTxnId);

    int markEscrowFailed(@Param("orderId") UUID orderId);

    int markReleasePending(@Param("orderId") UUID orderId);

    int markReleaseSucceeded(@Param("orderId") UUID orderId, @Param("releaseTxnId") UUID releaseTxnId);

    int markRefundPending(@Param("orderId") UUID orderId);

    int markEscrowCancelPending(@Param("orderId") UUID orderId);

    int markEscrowCancelRefundPending(@Param("orderId") UUID orderId, @Param("escrowTxnId") UUID escrowTxnId);

    int markCancelledNoRefund(@Param("orderId") UUID orderId);

    int markCancelledWithRefund(@Param("orderId") UUID orderId, @Param("refundTxnId") UUID refundTxnId);

    int markDisputeRefundPending(@Param("orderId") UUID orderId);

    int markDisputeReleasePending(@Param("orderId") UUID orderId);

    int markDisputeRefundSucceeded(@Param("orderId") UUID orderId, @Param("refundTxnId") UUID refundTxnId);

    int markCompleted(@Param("orderId") UUID orderId, @Param("releaseTxnId") UUID releaseTxnId);

    int markCancelled(@Param("orderId") UUID orderId, @Param("refundTxnId") UUID refundTxnId);

    int markDisputed(@Param("orderId") UUID orderId);

    int markRefunded(@Param("orderId") UUID orderId, @Param("refundTxnId") UUID refundTxnId);

    int updateStatus(@Param("orderId") UUID orderId, @Param("status") String status);

    List<MarketOrder> selectDueForAutoConfirm(@Param("asOf") Date asOf);

    List<MarketOrder> selectWalletPendingOrders(@Param("limit") int limit);
}
