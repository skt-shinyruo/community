package com.nowcoder.community.market.mapper;

import com.nowcoder.community.market.entity.VirtualOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface VirtualOrderMapper {

    int insert(VirtualOrder order);

    VirtualOrder selectById(@Param("orderId") long orderId);

    VirtualOrder selectByIdForUpdate(@Param("orderId") long orderId);

    VirtualOrder selectByRequestId(@Param("requestId") String requestId);

    List<VirtualOrder> selectByBuyerUserId(@Param("buyerUserId") int buyerUserId);

    List<VirtualOrder> selectBySellerUserId(@Param("sellerUserId") int sellerUserId);

    int markDelivered(@Param("orderId") long orderId, @Param("autoConfirmAt") java.util.Date autoConfirmAt);

    int markCompleted(@Param("orderId") long orderId, @Param("releaseTxnId") long releaseTxnId);

    int markCancelled(@Param("orderId") long orderId, @Param("refundTxnId") long refundTxnId);
}
