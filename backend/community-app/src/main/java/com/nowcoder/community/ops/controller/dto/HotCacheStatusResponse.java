package com.nowcoder.community.ops.controller.dto;

import java.time.Instant;
import java.util.UUID;

public record HotCacheStatusResponse(
        String scope,
        UUID boardId,
        String rankVersion,
        long itemCount,
        boolean summaryCacheAvailable,
        boolean degraded,
        String degradedReason,
        Instant lastPrewarmAt
) {
}
