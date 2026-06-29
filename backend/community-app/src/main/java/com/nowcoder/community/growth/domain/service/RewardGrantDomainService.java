package com.nowcoder.community.growth.domain.service;

import java.util.UUID;

public final class RewardGrantDomainService {

    public boolean hasValidSourceEventId(String sourceEventId) {
        return sourceEventId != null && !sourceEventId.isBlank();
    }

    public String taskRewardGrantId(UUID userId, String taskCode, String periodKey) {
        return "task:" + userId + ":" + taskCode + ":" + periodKey;
    }

    public long walletRewardAmount(int rewardBalanceDelta) {
        return rewardBalanceDelta;
    }
}
