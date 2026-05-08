package com.nowcoder.community.oss.application.result;

import java.util.UUID;

public record ObjectMetadataResult(
        UUID objectId,
        UUID currentVersionId,
        String usage,
        String ownerService,
        String ownerDomain,
        String ownerType,
        String ownerId,
        String visibility,
        String status,
        String fileName,
        String contentType,
        long contentLength,
        String checksumSha256,
        String publicUrl
) {
}
