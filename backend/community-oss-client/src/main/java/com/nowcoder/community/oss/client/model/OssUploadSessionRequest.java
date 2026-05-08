package com.nowcoder.community.oss.client.model;

public record OssUploadSessionRequest(
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
        String aliasKey,
        String actorId
) {
}
