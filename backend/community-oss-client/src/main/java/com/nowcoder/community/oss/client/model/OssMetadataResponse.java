package com.nowcoder.community.oss.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OssMetadataResponse(
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
