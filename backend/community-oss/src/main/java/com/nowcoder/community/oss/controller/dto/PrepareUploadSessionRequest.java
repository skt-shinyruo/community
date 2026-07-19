package com.nowcoder.community.oss.controller.dto;

import java.util.UUID;

public record PrepareUploadSessionRequest(
        UUID requestId,
        String usage,
        String ownerService,
        String ownerDomain,
        String visibility,
        String fileName,
        String contentType,
        long contentLength,
        String checksumSha256
) {
}
