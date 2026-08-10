package com.nowcoder.community.user.controller.dto;

import java.util.List;
import java.util.Map;

public record AvatarUploadSessionResponse(
        String uploadId,
        String objectId,
        String versionId,
        UploadInstruction upload,
        Constraints constraints,
        String expiresAt
) {

    public record UploadInstruction(
            String url,
            String method,
            String fileField,
            Map<String, String> fields,
            Map<String, String> headers
    ) {
        public UploadInstruction {
            fields = fields == null ? Map.of() : Map.copyOf(fields);
            headers = headers == null ? Map.of() : Map.copyOf(headers);
        }
    }

    public record Constraints(long maxBytes, List<String> mimeTypes) {
        public Constraints {
            mimeTypes = mimeTypes == null ? List.of() : List.copyOf(mimeTypes);
        }
    }
}
