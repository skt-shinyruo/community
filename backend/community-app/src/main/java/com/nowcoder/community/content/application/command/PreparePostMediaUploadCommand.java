package com.nowcoder.community.content.application.command;

import java.util.UUID;

public record PreparePostMediaUploadCommand(
        UUID actorUserId,
        UUID requestId,
        String fileName,
        String contentType,
        long contentLength,
        String mediaKind,
        String checksumSha256
) {

    public PreparePostMediaUploadCommand(
            UUID actorUserId,
            String fileName,
            String contentType,
            long contentLength,
            String mediaKind,
            String checksumSha256
    ) {
        this(actorUserId, null, fileName, contentType, contentLength, mediaKind, checksumSha256);
    }
}
