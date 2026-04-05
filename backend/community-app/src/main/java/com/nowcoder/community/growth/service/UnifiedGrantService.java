package com.nowcoder.community.growth.service;

import com.nowcoder.community.growth.api.action.GrowthGrantActionApi;
import com.nowcoder.community.growth.mapper.RewardGrantRecordMapper;
import com.nowcoder.community.wallet.api.action.WalletRewardActionApi;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnifiedGrantService implements GrowthGrantActionApi {

    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String POINTS_GRANT_SUFFIX = ":points";
    private static final String POINTS_SOURCE_MODULE = "points";
    private static final String POINTS_PROJECTION_REMARK = "points-projection";

    private final RewardGrantRecordMapper rewardGrantRecordMapper;
    private final WalletRewardActionApi walletRewardActionApi;

    public UnifiedGrantService(
            RewardGrantRecordMapper rewardGrantRecordMapper,
            WalletRewardActionApi walletRewardActionApi
    ) {
        this.rewardGrantRecordMapper = rewardGrantRecordMapper;
        this.walletRewardActionApi = walletRewardActionApi;
    }

    @Transactional
    @Override
    public boolean applyPointsProjection(int userId, String sourceEventId, String sourceEventType, int growthDelta) {
        return applyGrant(
                userId,
                sourceEventId + POINTS_GRANT_SUFFIX,
                sourceEventType,
                sourceEventId,
                sourceEventType,
                growthDelta,
                0,
                POINTS_SOURCE_MODULE,
                POINTS_PROJECTION_REMARK
        );
    }

    @Transactional
    public boolean applyGrant(
            int userId,
            String grantId,
            String grantType,
            String sourceEventId,
            String sourceEventType,
            int growthDelta,
            int rewardDelta,
            String sourceModule,
            String remark
    ) {
        try {
            rewardGrantRecordMapper.insert(
                    grantId,
                    userId,
                    grantType,
                    sourceEventId,
                    sourceEventType,
                    growthDelta,
                    rewardDelta,
                    STATUS_SUCCEEDED
            );
        } catch (DataIntegrityViolationException e) {
            return false;
        }

        long walletDelta = (long) growthDelta + rewardDelta;
        if (walletDelta != 0) {
            walletRewardActionApi.applyDelta(grantId, userId, walletDelta, grantType);
        }

        return true;
    }
}
