package com.nowcoder.community.content.application.result;

import java.util.Date;
import java.util.UUID;

public record ModerationActionResult(
        UUID id,
        UUID reportId,
        UUID actorId,
        String action,
        String reason,
        int durationSeconds,
        Date createTime
) {
}
