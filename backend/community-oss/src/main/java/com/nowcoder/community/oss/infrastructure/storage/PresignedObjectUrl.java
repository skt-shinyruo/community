package com.nowcoder.community.oss.infrastructure.storage;

import java.time.Instant;
import java.util.Map;

public record PresignedObjectUrl(
        String url,
        String method,
        Instant expiresAt,
        Map<String, String> requiredHeaders
) {

    public PresignedObjectUrl {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        method = method == null || method.isBlank() ? "GET" : method.trim().toUpperCase();
        expiresAt = expiresAt == null ? Instant.EPOCH : expiresAt;
        requiredHeaders = requiredHeaders == null ? Map.of() : Map.copyOf(requiredHeaders);
    }
}
