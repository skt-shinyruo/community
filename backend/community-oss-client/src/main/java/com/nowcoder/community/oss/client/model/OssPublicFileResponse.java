package com.nowcoder.community.oss.client.model;

public record OssPublicFileResponse(
        byte[] content,
        String contentType,
        long contentLength,
        String etag,
        String cacheControl,
        String fileName
) {
    public OssPublicFileResponse {
        content = content == null ? new byte[0] : content.clone();
        contentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType.trim();
        contentLength = Math.max(0, contentLength);
        etag = etag == null ? "" : etag.trim();
        cacheControl = cacheControl == null ? "" : cacheControl.trim();
        fileName = fileName == null ? "" : fileName.trim();
    }
}
