package com.nowcoder.community.oss.infrastructure.storage;

import java.io.InputStream;
import java.util.Objects;

public record StoredObject(InputStream content, String contentType, long contentLength) {

    public StoredObject {
        Objects.requireNonNull(content, "content");
        contentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType.trim();
        contentLength = Math.max(0, contentLength);
    }
}
