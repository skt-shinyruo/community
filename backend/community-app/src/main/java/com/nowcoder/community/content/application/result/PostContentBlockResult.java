package com.nowcoder.community.content.application.result;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record PostContentBlockResult(
        UUID id,
        int index,
        String type,
        String text,
        UUID assetId,
        String language,
        String caption,
        String displayName,
        Map<String, Object> metadata,
        PostMediaViewResult media
) {
    public PostContentBlockResult {
        metadata = copyMetadata(metadata);
    }

    private static Map<String, Object> copyMetadata(Map<String, Object> metadata) {
        return metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
