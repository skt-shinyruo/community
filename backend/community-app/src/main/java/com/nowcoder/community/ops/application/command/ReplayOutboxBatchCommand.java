package com.nowcoder.community.ops.application.command;

import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

public record ReplayOutboxBatchCommand(
        UUID actorUserId,
        String topic,
        String status,
        Instant createdFrom,
        Instant createdTo,
        int limit,
        String reason
) {

    public ReplayOutboxBatchCommand normalized() {
        return new ReplayOutboxBatchCommand(
                actorUserId,
                trim(topic),
                trim(status),
                createdFrom,
                createdTo,
                limit,
                trim(reason)
        );
    }

    public String normalizedReason() {
        return StringUtils.hasText(reason) ? reason.trim() : "";
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
