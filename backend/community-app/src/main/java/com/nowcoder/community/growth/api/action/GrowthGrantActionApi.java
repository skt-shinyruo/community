package com.nowcoder.community.growth.api.action;

import java.util.UUID;

public interface GrowthGrantActionApi {

    boolean applyPointsProjection(
            UUID userId,
            String sourceEventId,
            String sourceEventType,
            int growthDelta
    );
}
