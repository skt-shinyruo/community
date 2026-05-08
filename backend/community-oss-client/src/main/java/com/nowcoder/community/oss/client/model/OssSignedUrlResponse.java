package com.nowcoder.community.oss.client.model;

import java.time.Instant;

public record OssSignedUrlResponse(
        String url,
        String method,
        Instant expiresAt,
        String cacheControl
) {
}
