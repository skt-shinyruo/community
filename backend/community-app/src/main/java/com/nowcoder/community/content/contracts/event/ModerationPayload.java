package com.nowcoder.community.content.contracts.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Moderation notification event payload (report/action/punishment -> user notice).
 */
public record ModerationPayload(
        UUID reportId,
        String kind,
        UUID toUserId,
        UUID actorUserId,
        Integer targetType,
        UUID targetId,
        String action,
        String reason,
        Integer durationSeconds,
        Instant createTime
) {
}
