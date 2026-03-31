package com.nowcoder.community.common.outbox;

import java.time.Instant;

/**
 * A row from {@code community.outbox_event}.
 */
public record OutboxEvent(
        long id,
        String eventId,
        String topic,
        String eventKey,
        String payload,
        String status,
        int retryCount,
        Instant nextRetryAt,
        String lastError
) {
}
