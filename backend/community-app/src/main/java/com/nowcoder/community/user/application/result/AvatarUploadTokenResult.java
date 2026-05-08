package com.nowcoder.community.user.application.result;

import java.time.Instant;

public record AvatarUploadTokenResult(
        String uploadId,
        String fileKey,
        String uploadUrl,
        String uploadMethod,
        String fileField,
        String fileKeyField,
        long maxBytes,
        String mimeLimit,
        Instant expiresAt
) {
}
