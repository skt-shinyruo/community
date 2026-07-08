package com.nowcoder.community.content.api.model;

public record UpdateHotFeedDegradationSignalRequest(
        boolean degraded,
        String reason
) {
}
