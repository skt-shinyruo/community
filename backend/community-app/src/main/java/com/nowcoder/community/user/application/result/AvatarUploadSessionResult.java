package com.nowcoder.community.user.application.result;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AvatarUploadSessionResult(
        String uploadId,
        UUID objectId,
        UUID versionId,
        String uploadUrl,
        String uploadMethod,
        String fileField,
        Map<String, String> fields,
        Map<String, String> headers,
        long maxBytes,
        List<String> mimeTypes,
        Instant expiresAt
) {

    public AvatarUploadSessionResult {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        mimeTypes = mimeTypes == null ? List.of() : List.copyOf(mimeTypes);
    }
}
