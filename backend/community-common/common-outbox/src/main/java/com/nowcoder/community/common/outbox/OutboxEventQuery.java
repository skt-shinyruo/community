package com.nowcoder.community.common.outbox;

import org.springframework.util.StringUtils;

import java.time.Instant;

public record OutboxEventQuery(
        String status,
        String topic,
        String eventId,
        Instant createdFrom,
        Instant createdTo,
        int limit
) {

    public int safeLimit() {
        return Math.min(500, Math.max(1, limit <= 0 ? 50 : limit));
    }

    public String normalizedStatus() {
        return StringUtils.hasText(status) ? status.trim() : null;
    }

    public String normalizedTopic() {
        return StringUtils.hasText(topic) ? topic.trim() : null;
    }

    public String normalizedEventId() {
        return StringUtils.hasText(eventId) ? eventId.trim() : null;
    }
}
