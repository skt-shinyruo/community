package com.nowcoder.community.oss.controller.dto;

import java.util.UUID;

public record InternalPrepareUploadSessionRequest(
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
}
