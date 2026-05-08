package com.nowcoder.community.oss.application.command;

public record PrepareObjectUploadCommand(
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
