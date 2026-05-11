package com.nowcoder.community.oss.controller.dto;

public record PrepareUploadSessionRequest(
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
