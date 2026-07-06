package com.nowcoder.community.analytics.infrastructure.event;

import java.util.UUID;

public record AnalyticsRequestEvent(
        String ip,
        UUID userId,
        boolean recordUv,
        boolean recordDau
) {
}
