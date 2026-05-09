package com.nowcoder.community.drive.application.result;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DriveUploadSessionResult(
        String uploadId,
        String fileKey,
        UploadInstruction upload,
        UploadConstraints constraints,
        Instant expiresAt
) {
    public record UploadInstruction(
            String url,
            String method,
            String fileField,
            Map<String, String> fields,
            Map<String, String> headers
    ) {
    }

    public record UploadConstraints(long maxBytes, List<String> mimeTypes) {
    }
}
