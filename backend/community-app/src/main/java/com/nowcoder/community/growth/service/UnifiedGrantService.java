package com.nowcoder.community.growth.service;

import com.nowcoder.community.growth.api.action.GrowthGrantActionApi;
import com.nowcoder.community.growth.mapper.RewardGrantRecordMapper;
import com.nowcoder.community.user.api.action.UserPointsActionApi;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnifiedGrantService implements GrowthGrantActionApi {

    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String POINTS_GRANT_SUFFIX = ":points";
    private static final String POINTS_SOURCE_MODULE = "points";
    private static final String POINTS_PROJECTION_REMARK = "points-projection";

    private final UserPointsActionApi userPointsActionApi;
    private final RewardAccountService rewardAccountService;
    private final RewardGrantRecordMapper rewardGrantRecordMapper;

    public UnifiedGrantService(
            UserPointsActionApi userPointsActionApi,
            RewardAccountService rewardAccountService,
            RewardGrantRecordMapper rewardGrantRecordMapper
    ) {
        this.userPointsActionApi = userPointsActionApi;
        this.rewardAccountService = rewardAccountService;
        this.rewardGrantRecordMapper = rewardGrantRecordMapper;
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

        if (growthDelta != 0) {
            userPointsActionApi.applyPoints(userId, grantId, grantType, growthDelta);
        }
        if (rewardDelta != 0) {
            rewardAccountService.applyAvailableDelta(userId, grantId, grantType, rewardDelta, sourceModule, remark);
        }

        return true;
    }
}
