package com.nowcoder.community.user.application.command;

import java.util.UUID;

public record ApplyUserModerationCommand(
        UUID userId,
        String action,
        int durationSeconds
) {
}
