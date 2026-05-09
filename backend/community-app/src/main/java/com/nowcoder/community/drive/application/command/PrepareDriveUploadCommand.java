package com.nowcoder.community.drive.application.command;

import java.util.UUID;

public record PrepareDriveUploadCommand(
        UUID actorUserId,
        UUID parentId,
        String fileName,
        String contentType,
        long contentLength,
        String checksumSha256
) {
}
