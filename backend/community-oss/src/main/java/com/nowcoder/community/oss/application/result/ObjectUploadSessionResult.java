package com.nowcoder.community.oss.application.result;

import java.time.Instant;
import java.util.UUID;

public record ObjectUploadSessionResult(
        UUID sessionId,
        UUID objectId,
        UUID versionId,
        String uploadMode,
        String uploadUrl,
        Instant expiresAt
) {
}
