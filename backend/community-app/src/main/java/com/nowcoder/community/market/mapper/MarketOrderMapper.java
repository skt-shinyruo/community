package com.nowcoder.community.market.mapper;

import com.nowcoder.community.market.entity.MarketOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
@Mapper
public interface MarketOrderMapper {

    int insert(MarketOrder order);

    MarketOrder selectById(@Param("orderId") long orderId);

    MarketOrder selectByIdForUpdate(@Param("orderId") long orderId);

    MarketOrder selectByRequestId(@Param("requestId") String requestId);

    List<MarketOrder> selectByBuyerUserId(@Param("buyerUserId") int buyerUserId);

    List<MarketOrder> selectBySellerUserId(@Param("sellerUserId") int sellerUserId);

    int markDelivered(@Param("orderId") long orderId, @Param("autoConfirmAt") Date autoConfirmAt);

    int markShipped(@Param("orderId") long orderId, @Param("autoConfirmAt") Date autoConfirmAt);

    int markCompleted(@Param("orderId") long orderId, @Param("releaseTxnId") long releaseTxnId);

    int markCancelled(@Param("orderId") long orderId, @Param("refundTxnId") long refundTxnId);

    int markDisputed(@Param("orderId") long orderId);

    int markRefunded(@Param("orderId") long orderId, @Param("refundTxnId") long refundTxnId);

    int updateStatus(@Param("orderId") long orderId, @Param("status") String status);

    List<MarketOrder> selectDueForAutoConfirm(@Param("asOf") Date asOf);
}
