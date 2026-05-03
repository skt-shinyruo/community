package com.nowcoder.community.common.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * A row from {@code community.outbox_event}.
 */
public record OutboxEvent(
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
        String traceparent
) {
}
