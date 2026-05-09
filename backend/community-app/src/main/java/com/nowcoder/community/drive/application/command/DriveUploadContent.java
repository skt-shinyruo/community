package com.nowcoder.community.drive.application.command;

import java.io.IOException;
import java.io.InputStream;

public record DriveUploadContent(
        UploadStream uploadStream,
        String contentType,
        long contentLength,
        String checksumSha256
) {
    public InputStream openStream() throws IOException {
        InputStream stream = uploadStream.openStream();
        return stream == null ? InputStream.nullInputStream() : stream;
    }

    @FunctionalInterface
    public interface UploadStream {
        InputStream openStream() throws IOException;
    }
}
