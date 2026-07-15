package com.nowcoder.community.oss.application.command;

import java.util.UUID;

public record PrepareObjectUploadCommand(
        UUID requestId,
        String usage,
        String ownerService,
        String ownerDomain,
        String ownerType,
        String ownerId,
        String visibility,
        String fileName,
        String contentType,
        long contentLength,
        String checksumSha256,
        String actorId
) {

    public PrepareObjectUploadCommand(
            String usage,
            String ownerService,
            String ownerDomain,
            String ownerType,
            String ownerId,
            String visibility,
            String fileName,
            String contentType,
            long contentLength,
            String checksumSha256,
            String actorId
    ) {
        this(
                null,
                usage,
                ownerService,
                ownerDomain,
                ownerType,
                ownerId,
                visibility,
                fileName,
                contentType,
                contentLength,
                checksumSha256,
                actorId
        );
    }
}
