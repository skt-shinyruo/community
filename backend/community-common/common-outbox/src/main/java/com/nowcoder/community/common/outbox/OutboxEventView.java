package com.nowcoder.community.common.outbox;

import java.time.Instant;
import java.util.UUID;

public record OutboxEventView(
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
