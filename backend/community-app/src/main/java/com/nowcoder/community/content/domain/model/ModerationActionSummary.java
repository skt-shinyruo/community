package com.nowcoder.community.content.domain.model;

import java.util.Date;
import java.util.UUID;

public record ModerationActionSummary(
        UUID id,
        UUID reportId,
        UUID actorId,
        String action,
        String reason,
        int durationSeconds,
        Date createTime
) {
}
