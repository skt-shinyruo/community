package com.nowcoder.community.ops.application.command;

import java.time.Instant;

public record FindOutboxEventsCommand(
        String status,
        String topic,
        String eventId,
        Instant createdFrom,
        Instant createdTo,
        int limit
) {
    public FindOutboxEventsCommand normalized() {
        return new FindOutboxEventsCommand(
                trim(status),
                trim(topic),
                trim(eventId),
                createdFrom,
                createdTo,
                Math.min(500, Math.max(1, limit <= 0 ? 50 : limit))
        );
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
