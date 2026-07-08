package com.nowcoder.community.ops.controller.dto;

import java.time.Instant;
import java.util.UUID;

public record HotCachePrewarmResponse(
        String scope,
        UUID boardId,
        int requestedCount,
        int loadedCount,
        int warmedCount,
        String rankVersion,
        boolean degraded,
        String degradedReason,
        Instant lastPrewarmAt
) {
}
