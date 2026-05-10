package com.nowcoder.community.drive.application.command;

import java.util.UUID;

public record CreateDriveFolderCommand(
        UUID actorUserId,
        UUID parentId,
        String name
) {
}
