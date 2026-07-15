package com.nowcoder.community.oss.client.model;

import java.util.UUID;

public record OssUploadSessionRequest(
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

    public OssUploadSessionRequest(
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
