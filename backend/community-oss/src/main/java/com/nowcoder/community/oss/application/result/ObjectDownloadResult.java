package com.nowcoder.community.oss.application.result;

import java.io.InputStream;
import java.util.Objects;

public record ObjectDownloadResult(
        InputStream content,
        String contentType,
        long contentLength,
        String etag,
        String cacheControl,
        String fileName
) {

    public ObjectDownloadResult {
        Objects.requireNonNull(content, "content");
        contentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType.trim();
        contentLength = Math.max(0, contentLength);
        etag = etag == null ? "" : etag.trim();
        cacheControl = cacheControl == null ? "" : cacheControl.trim();
        fileName = fileName == null ? "" : fileName.trim();
    }
}
