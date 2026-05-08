package com.nowcoder.community.oss.application.command;

import java.io.InputStream;
import java.util.Objects;
import java.util.function.Supplier;

public record ObjectUploadContent(
        Supplier<InputStream> contentSupplier,
        String contentType,
        long contentLength,
        String checksumSha256
) {

    public ObjectUploadContent {
        Objects.requireNonNull(contentSupplier, "contentSupplier");
        contentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType.trim();
        contentLength = Math.max(0, contentLength);
        checksumSha256 = checksumSha256 == null ? "" : checksumSha256.trim();
    }

    public InputStream openStream() {
        InputStream stream = contentSupplier.get();
        if (stream == null) {
            throw new IllegalArgumentException("upload content supplier returned null");
        }
        return stream;
    }
}
