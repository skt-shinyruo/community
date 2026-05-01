package com.nowcoder.community.user.application.result;

import java.io.InputStream;
import java.util.Objects;

public record AvatarFileResult(InputStream content, String contentType, long contentLength) {

    public AvatarFileResult {
        Objects.requireNonNull(content, "content");
        contentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        contentLength = Math.max(-1, contentLength);
    }
}
