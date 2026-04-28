package com.nowcoder.community.user.application.command;

import java.util.UUID;

public record UpdateUserRoleCommand(
        UUID actorUserId,
        UUID targetUserId,
        int type,
        String reason,
        boolean confirm
) {
}
