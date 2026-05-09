package com.nowcoder.community.content.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record PostContentBlock(
        UUID id,
        UUID postId,
        int index,
        String type,
        String text,
        UUID mediaAssetId,
        String language,
        String caption,
        String displayName,
        Map<String, Object> metadata
) {
    public PostContentBlock {
        metadata = copyMetadata(metadata);
    }

    private static Map<String, Object> copyMetadata(Map<String, Object> metadata) {
        return metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
