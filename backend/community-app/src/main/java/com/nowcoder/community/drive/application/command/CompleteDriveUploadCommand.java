package com.nowcoder.community.drive.application.command;

import java.util.UUID;

public record CompleteDriveUploadCommand(
        UUID actorUserId,
        UUID uploadId,
        DriveUploadContent content
) {
}
