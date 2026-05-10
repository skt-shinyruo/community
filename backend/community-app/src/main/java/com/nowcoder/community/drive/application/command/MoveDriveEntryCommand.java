package com.nowcoder.community.drive.application.command;

import java.util.UUID;

public record MoveDriveEntryCommand(
        UUID actorUserId,
        UUID entryId,
        UUID targetParentId
) {
}
