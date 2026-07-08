package com.nowcoder.community.ops.application.result;

import java.util.List;

public record OutboxBatchReplayResult(
        String topic,
        int requestedCount,
        int replayedCount,
        int rejectedCount,
        int notRequeuedCount,
        String result,
        List<OutboxBatchReplayItemResult> items
) {
    public OutboxBatchReplayResult {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
