package com.nowcoder.community.oss.client.model;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

public record OssCompleteUploadRequest(
        UUID sessionId,
        UUID objectId,
        UUID versionId,
        UploadStream uploadStream,
        String fileName,
        String contentType,
        long contentLength,
        String checksumSha256
) {

    public OssCompleteUploadRequest {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(objectId, "objectId");
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(uploadStream, "uploadStream");
        fileName = fileName == null || fileName.isBlank() ? "object.bin" : fileName.trim();
        contentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType.trim();
        contentLength = Math.max(0, contentLength);
        checksumSha256 = checksumSha256 == null ? "" : checksumSha256.trim();
    }

    public InputStream openStream() throws IOException {
        InputStream stream = uploadStream.openStream();
        if (stream == null) {
            return InputStream.nullInputStream();
        }
        return stream;
    }

    @FunctionalInterface
    public interface UploadStream {
        InputStream openStream() throws IOException;
    }
}
