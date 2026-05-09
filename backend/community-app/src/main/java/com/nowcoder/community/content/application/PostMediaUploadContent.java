package com.nowcoder.community.content.application;

import java.io.IOException;
import java.io.InputStream;

public record PostMediaUploadContent(
        UploadStream uploadStream,
        String contentType,
        long size,
        String checksumSha256
) {

    public PostMediaUploadContent {
        contentType = contentType == null ? "" : contentType.trim().toLowerCase();
        checksumSha256 = checksumSha256 == null ? "" : checksumSha256.trim();
    }

    public InputStream openStream() throws IOException {
        if (uploadStream == null) {
            return InputStream.nullInputStream();
        }
        InputStream stream = uploadStream.openStream();
        return stream == null ? InputStream.nullInputStream() : stream;
    }

    public boolean empty() {
        return size <= 0;
    }

    @FunctionalInterface
    public interface UploadStream {
        InputStream openStream() throws IOException;
    }
}
