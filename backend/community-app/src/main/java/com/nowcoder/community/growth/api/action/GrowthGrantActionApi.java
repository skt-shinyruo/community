package com.nowcoder.community.growth.api.action;

public interface GrowthGrantActionApi {

    boolean applyPointsProjection(
            int userId,
            String sourceEventId,
            String sourceEventType,
            int growthDelta
    );
}
