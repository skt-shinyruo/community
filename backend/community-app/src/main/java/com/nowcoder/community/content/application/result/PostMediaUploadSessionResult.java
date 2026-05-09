package com.nowcoder.community.content.application.result;

import java.time.Instant;
import java.util.UUID;

public record PostMediaUploadSessionResult(
        UUID assetId,
        String uploadId,
        String uploadUrl,
        String uploadMethod,
        String fileField,
        String uploadIdField,
        long maxBytes,
        String mimeTypes,
        Instant expiresAt,
        UUID ossObjectId,
        UUID ossVersionId
) {
}
