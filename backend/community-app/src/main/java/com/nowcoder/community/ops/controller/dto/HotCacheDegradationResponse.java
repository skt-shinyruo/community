package com.nowcoder.community.ops.controller.dto;

import java.time.Instant;

public record HotCacheDegradationResponse(
        boolean degraded,
        String reason,
        Instant updatedAt
) {
}
