package com.nowcoder.community.market.infrastructure.persistence.mapper;

import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketOrderDataObject;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketOrderTransitionDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface MarketOrderMapper {

    int insert(MarketOrderDataObject order);

    MarketOrderDataObject selectById(@Param("orderId") UUID orderId);

    MarketOrderDataObject selectByIdForUpdate(@Param("orderId") UUID orderId);

    MarketOrderDataObject selectByRequestId(@Param("requestId") String requestId);

    MarketOrderDataObject selectByRequestIdForUpdate(@Param("requestId") String requestId);

    MarketOrderDataObject selectByBuyerUserIdAndRequestId(@Param("buyerUserId") UUID buyerUserId,
                                                          @Param("requestId") String requestId);

    MarketOrderDataObject selectByBuyerUserIdAndRequestIdForUpdate(@Param("buyerUserId") UUID buyerUserId,
                                                                   @Param("requestId") String requestId);

    List<MarketOrderDataObject> selectByBuyerUserId(@Param("buyerUserId") UUID buyerUserId,
                                                    @Param("offset") long offset,
                                                    @Param("limit") int limit);

    List<MarketOrderDataObject> selectBySellerUserId(@Param("sellerUserId") UUID sellerUserId,
                                                     @Param("offset") long offset,
                                                     @Param("limit") int limit);

    int apply(MarketOrderTransitionDataObject transition);

    List<MarketOrderDataObject> selectDueForAutoConfirm(@Param("asOf") Date asOf,
                                                        @Param("limit") int limit);

    int deferAutoConfirm(@Param("orderId") UUID orderId,
                         @Param("asOf") Date asOf,
                         @Param("nextAttemptAt") Date nextAttemptAt);

    List<MarketOrderDataObject> selectWalletPendingOrders(@Param("limit") int limit);

    int deferWalletRecovery(@Param("orderId") UUID orderId,
                            @Param("asOf") Date asOf,
                            @Param("nextAttemptAt") Date nextAttemptAt);
}
