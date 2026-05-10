package com.nowcoder.community.oss.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OssMetadataResponse(
        UUID objectId,
        UUID currentVersionId,
        String usage,
        String status,
        String contentType,
        long contentLength,
        String publicUrl
) {
}
