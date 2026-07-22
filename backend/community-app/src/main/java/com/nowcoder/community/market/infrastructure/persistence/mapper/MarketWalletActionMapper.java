package com.nowcoder.community.market.infrastructure.persistence.mapper;

import com.nowcoder.community.market.domain.model.MarketWalletActionLease;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketWalletActionDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface MarketWalletActionMapper {

    int insert(MarketWalletActionDataObject action);

    MarketWalletActionDataObject selectById(@Param("actionId") UUID actionId);

    MarketWalletActionDataObject selectByRequestId(@Param("requestId") String requestId);

    MarketWalletActionDataObject selectByOrderAndType(@Param("orderId") UUID orderId, @Param("actionType") String actionType);

    List<MarketWalletActionDataObject> selectDue(@Param("asOf") Date asOf, @Param("limit") int limit);

    List<MarketWalletActionDataObject> selectUnfinishedWithWalletTxn(@Param("limit") int limit);

    int claimProcessing(@Param("lease") MarketWalletActionLease lease, @Param("leaseUntil") Date leaseUntil);

    int markSucceeded(@Param("lease") MarketWalletActionLease lease,
                      @Param("walletTxnId") UUID walletTxnId,
                      @Param("resultType") String resultType);

    int markCancelled(@Param("lease") MarketWalletActionLease lease, @Param("resultType") String resultType);

    int cancelPendingEscrow(@Param("requestId") String requestId, @Param("resultType") String resultType);

    int markRetrying(@Param("lease") MarketWalletActionLease lease,
                     @Param("nextRetryAt") Date nextRetryAt,
                     @Param("lastError") String lastError);

    int markFailed(@Param("lease") MarketWalletActionLease lease,
                   @Param("failureCode") String failureCode,
                   @Param("lastError") String lastError);

    int markRecoveryPending(@Param("lease") MarketWalletActionLease lease,
                            @Param("walletTxnId") UUID walletTxnId,
                            @Param("failureCode") String failureCode,
                            @Param("lastError") String lastError);

    int markDead(@Param("lease") MarketWalletActionLease lease, @Param("lastError") String lastError);

    int markRecoveredSucceeded(@Param("actionId") UUID actionId,
                               @Param("expectedStatus") String expectedStatus,
                               @Param("walletTxnId") UUID walletTxnId,
                               @Param("resultType") String resultType);

    int rescheduleFailed(@Param("actionId") UUID actionId,
                         @Param("expectedFailureCode") String expectedFailureCode,
                         @Param("nextRetryAt") Date nextRetryAt,
                         @Param("lastError") String lastError);

    int recoverExpiredProcessing(@Param("asOf") Date asOf);
}
