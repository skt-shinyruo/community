package com.nowcoder.community.content.application.result;

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
}
