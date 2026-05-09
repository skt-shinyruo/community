package com.nowcoder.community.content.application.command;

import java.util.UUID;

public record PreparePostMediaUploadCommand(
        UUID actorUserId,
        String fileName,
        String contentType,
        long contentLength,
        String mediaKind,
        String checksumSha256
) {
}
