package com.nowcoder.community.ops.controller.dto;

import java.util.UUID;

public record OutboxBatchReplayItemResponse(
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
