package com.nowcoder.community.drive.application.command;

import java.util.UUID;

public record RenameDriveEntryCommand(
        UUID actorUserId,
        UUID entryId,
        String newName
) {
}
