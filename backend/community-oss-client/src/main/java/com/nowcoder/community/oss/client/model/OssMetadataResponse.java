package com.nowcoder.community.oss.client.model;

import java.util.UUID;

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
