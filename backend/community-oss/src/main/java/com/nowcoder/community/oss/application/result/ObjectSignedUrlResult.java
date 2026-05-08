package com.nowcoder.community.oss.application.result;

import java.time.Instant;

public record ObjectSignedUrlResult(
        String url,
        String method,
        Instant expiresAt,
        String cacheControl
) {
}
