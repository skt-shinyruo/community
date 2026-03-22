package com.nowcoder.community.growth.service;

import com.nowcoder.community.growth.mapper.RewardGrantRecordMapper;
import com.nowcoder.community.user.service.PointsService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnifiedGrantService {

    private static final String STATUS_SUCCEEDED = "SUCCEEDED";

    private final PointsService pointsService;
    private final RewardAccountService rewardAccountService;
    private final RewardGrantRecordMapper rewardGrantRecordMapper;

    public UnifiedGrantService(
            PointsService pointsService,
            RewardAccountService rewardAccountService,
            RewardGrantRecordMapper rewardGrantRecordMapper
    ) {
        this.pointsService = pointsService;
        this.rewardAccountService = rewardAccountService;
        this.rewardGrantRecordMapper = rewardGrantRecordMapper;
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
            pointsService.applyPoints(userId, grantId, grantType, growthDelta);
        }
        if (rewardDelta != 0) {
            rewardAccountService.applyAvailableDelta(userId, grantId, grantType, rewardDelta, sourceModule, remark);
        }

        return true;
    }
}
