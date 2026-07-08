package com.nowcoder.community.ops.application.result;

import java.util.UUID;

public record OutboxBatchReplayItemResult(
        UUID outboxId,
        String eventId,
        String topic,
        String beforeStatus,
        String afterStatus,
        boolean replayed,
        String result,
        String message
) {
}
