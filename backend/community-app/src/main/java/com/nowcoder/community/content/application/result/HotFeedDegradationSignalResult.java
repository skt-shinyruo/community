package com.nowcoder.community.content.application.result;

import java.time.Instant;

public record HotFeedDegradationSignalResult(
        boolean degraded,
        String reason,
        Instant updatedAt
) {
}
