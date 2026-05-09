package com.nowcoder.community.content.api.model;

import java.util.Map;
import java.util.UUID;

public record PostContentBlockPayload(
        String type,
        String text,
        UUID assetId,
        String language,
        String caption,
        String displayName,
        Map<String, Object> metadata
) {
}
