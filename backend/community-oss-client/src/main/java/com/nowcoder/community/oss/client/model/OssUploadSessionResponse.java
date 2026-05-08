package com.nowcoder.community.oss.client.model;

import java.time.Instant;
import java.util.UUID;

public record OssUploadSessionResponse(
        UUID sessionId,
        UUID objectId,
        UUID versionId,
        String uploadMode,
        String uploadUrl,
        Instant expiresAt
) {
}
