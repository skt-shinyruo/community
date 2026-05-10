package com.nowcoder.community.drive.controller.dto;

import com.nowcoder.community.drive.application.result.DriveUploadSessionResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DriveUploadSessionResponse(
        String uploadId,
        String fileKey,
        UploadInstruction upload,
        UploadConstraints constraints,
        Instant expiresAt
) {
    public static DriveUploadSessionResponse from(DriveUploadSessionResult result) {
        if (result == null) {
            return null;
        }
        return new DriveUploadSessionResponse(
                result.uploadId(),
                result.fileKey(),
                UploadInstruction.from(result.upload()),
                UploadConstraints.from(result.constraints()),
                result.expiresAt()
        );
    }

    public record UploadInstruction(
            String url,
            String method,
            String fileField,
            Map<String, String> fields,
            Map<String, String> headers
    ) {
        private static UploadInstruction from(DriveUploadSessionResult.UploadInstruction result) {
            if (result == null) {
                return null;
            }
            return new UploadInstruction(
                    result.url(),
                    result.method(),
                    result.fileField(),
                    result.fields() == null ? Map.of() : Map.copyOf(result.fields()),
                    result.headers() == null ? Map.of() : Map.copyOf(result.headers())
            );
        }
    }

    public record UploadConstraints(long maxBytes, List<String> mimeTypes) {
        private static UploadConstraints from(DriveUploadSessionResult.UploadConstraints result) {
            if (result == null) {
                return null;
            }
            return new UploadConstraints(
                    result.maxBytes(),
                    result.mimeTypes() == null ? List.of() : List.copyOf(result.mimeTypes())
            );
        }
    }
}
