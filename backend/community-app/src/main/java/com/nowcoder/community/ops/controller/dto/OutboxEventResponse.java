package com.nowcoder.community.ops.controller.dto;

import java.time.Instant;
import java.util.UUID;

public record OutboxEventResponse(
        UUID outboxId,
        String eventId,
        String topic,
        String eventKey,
        String status,
        int retryCount,
        Instant nextRetryAt,
        String lastError,
        String traceId,
        Instant createdAt,
        Instant updatedAt
) {
}
