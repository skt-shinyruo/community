package com.nowcoder.community.content.api.model;

import java.time.Instant;

public record HotFeedDegradationSignalView(
        boolean degraded,
        String reason,
        Instant updatedAt
) {
}
