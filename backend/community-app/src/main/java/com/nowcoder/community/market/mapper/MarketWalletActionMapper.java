package com.nowcoder.community.market.mapper;

import com.nowcoder.community.market.entity.MarketWalletAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface MarketWalletActionMapper {

    int insert(MarketWalletAction action);

    MarketWalletAction selectById(@Param("actionId") UUID actionId);

    MarketWalletAction selectByRequestId(@Param("requestId") String requestId);

    List<MarketWalletAction> selectDue(@Param("asOf") Date asOf, @Param("limit") int limit);

    int claimProcessing(@Param("actionId") UUID actionId, @Param("leaseUntil") Date leaseUntil);

    int markSucceeded(@Param("actionId") UUID actionId,
                      @Param("walletTxnId") UUID walletTxnId,
                      @Param("resultType") String resultType);

    int markCancelled(@Param("actionId") UUID actionId, @Param("resultType") String resultType);

    int markRetrying(@Param("actionId") UUID actionId,
                     @Param("nextRetryAt") Date nextRetryAt,
                     @Param("lastError") String lastError);

    int markFailed(@Param("actionId") UUID actionId,
                   @Param("failureCode") String failureCode,
                   @Param("lastError") String lastError);

    int markDead(@Param("actionId") UUID actionId, @Param("lastError") String lastError);

    int recoverExpiredProcessing(@Param("asOf") Date asOf);
}
