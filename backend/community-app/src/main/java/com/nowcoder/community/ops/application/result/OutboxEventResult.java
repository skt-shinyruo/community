package com.nowcoder.community.ops.application.result;

import java.time.Instant;
import java.util.UUID;

public record OutboxEventResult(
        UUID id,
        String eventId,
        String topic,
        String eventKey,
        String payload,
        String status,
        int retryCount,
        Instant nextRetryAt,
        String lastError,
        String traceId,
        String traceparent,
        Instant createdAt,
        Instant updatedAt
) {
}
