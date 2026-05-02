package com.nowcoder.community.user.application;

import java.io.IOException;
import java.io.InputStream;

public record AvatarUploadContent(
        UploadStream uploadStream,
        String contentType,
        long size,
        boolean empty
) {

    public AvatarUploadContent {
        contentType = contentType == null ? "" : contentType.trim().toLowerCase();
    }

    public InputStream openStream() throws IOException {
        if (uploadStream == null) {
            return InputStream.nullInputStream();
        }
        return uploadStream.openStream();
    }

    @FunctionalInterface
    public interface UploadStream {
        InputStream openStream() throws IOException;
    }
}
