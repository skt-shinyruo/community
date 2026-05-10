package com.nowcoder.community.drive.application.command;

import java.time.Instant;
import java.util.UUID;

public record CreateDriveShareCommand(
        UUID actorUserId,
        UUID entryId,
        String password,
        Instant expiresAt
) {
}
