package com.nowcoder.community.content.application.command;

import java.util.UUID;

public record TakeModerationActionCommand(
        UUID actorId,
        UUID reportId,
        String action,
        String reason,
        Integer durationSeconds
) {
}
