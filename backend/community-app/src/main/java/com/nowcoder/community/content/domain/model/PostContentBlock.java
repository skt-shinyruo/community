package com.nowcoder.community.content.domain.model;

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
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
