package com.nowcoder.community.content.api.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PostContentBlockView(
        UUID id,
        int index,
        String type,
        String text,
        UUID assetId,
        String language,
        String caption,
        String displayName,
        Map<String, Object> metadata,
        PostMediaView media
) {
    public record PostMediaView(
            UUID assetId,
            String mediaKind,
            String lifecycle,
            String videoState,
            String fileName,
            String contentType,
            long contentLength,
            String url,
            String downloadUrl,
            String posterUrl,
            List<VideoSource> sources
    ) {
        public record VideoSource(String url, String contentType, Integer width, Integer height) {
        }
    }
}
