package com.nowcoder.community.oss.infrastructure.storage;

import java.time.Instant;
import java.util.Objects;

public record ObjectStoreObject(
        String bucket,
        String key,
        String contentType,
        long contentLength,
        String etag,
        Instant lastModified
) {

    public ObjectStoreObject {
        bucket = requireText(bucket, "bucket");
        key = requireText(key, "key");
        contentType = normalizeContentType(contentType);
        contentLength = Math.max(0, contentLength);
        etag = etag == null ? "" : etag;
        Objects.requireNonNull(lastModified, "lastModified");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeContentType(String value) {
        if (value == null || value.isBlank()) {
            return "application/octet-stream";
        }
        return value.trim();
    }
}
