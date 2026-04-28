package com.nowcoder.community.analytics.domain.model;

import java.util.UUID;

public record AnalyticsRequestEvent(
        String ip,
        UUID userId,
        boolean recordUv,
        boolean recordDau
) {
}
