package com.nowcoder.community.market.infrastructure.persistence.mapper;

import com.nowcoder.community.market.domain.model.MarketWalletActionClaim;
import com.nowcoder.community.market.domain.model.MarketWalletActionLease;
import com.nowcoder.community.market.domain.model.MarketWalletActionLeaseRecovery;
import com.nowcoder.community.market.domain.model.MarketWalletAction;
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

    MarketWalletAction selectByOrderAndType(@Param("orderId") UUID orderId, @Param("actionType") String actionType);

    List<MarketWalletAction> selectDue(
            @Param("asOf") Date asOf,
            @Param("maxRetryAttempts") int maxRetryAttempts,
            @Param("limit") int limit
    );

    List<MarketWalletAction> selectExpiredProcessing(@Param("asOf") Date asOf, @Param("limit") int limit);

    List<MarketWalletAction> selectUnfinishedWithWalletTxn(@Param("limit") int limit);

    int claimProcessing(@Param("claim") MarketWalletActionClaim claim);

    MarketWalletAction selectClaimed(@Param("lease") MarketWalletActionLease lease);

    MarketWalletAction selectClaimedForUpdate(@Param("lease") MarketWalletActionLease lease,
                                                        @Param("leaseValidAt") Date leaseValidAt);

    MarketWalletAction selectByIdForUpdate(@Param("actionId") UUID actionId);

    int recordWalletTxn(@Param("lease") MarketWalletActionLease lease,
                        @Param("walletTxnId") UUID walletTxnId,
                        @Param("leaseValidAt") Date leaseValidAt);

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
                         @Param("expectedRetryCount") int expectedRetryCount,
                         @Param("nextRetryAt") Date nextRetryAt,
                         @Param("maxRetryAttempts") int maxRetryAttempts,
                         @Param("lastError") String lastError);

    int recoverExpiredProcessing(@Param("recovery") MarketWalletActionLeaseRecovery recovery);

    int deferWalletTxnRecovery(@Param("actionId") UUID actionId,
                               @Param("expectedStatus") String expectedStatus,
                               @Param("walletTxnId") UUID walletTxnId,
                               @Param("nextRetryAt") Date nextRetryAt,
                               @Param("lastError") String lastError);

    int deferFailedRecovery(@Param("actionId") UUID actionId,
                            @Param("expectedStatus") String expectedStatus,
                            @Param("expectedFailureCode") String expectedFailureCode,
                            @Param("nextRetryAt") Date nextRetryAt,
                            @Param("lastError") String lastError);
}
