package com.nowcoder.community.ops.controller.dto;

import java.util.List;

public record OutboxBatchReplayResponse(
        String topic,
        int requestedCount,
        int replayedCount,
        int rejectedCount,
        int notRequeuedCount,
        String result,
        List<OutboxBatchReplayItemResponse> items
) {
}
