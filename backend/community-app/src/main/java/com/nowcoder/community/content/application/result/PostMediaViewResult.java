package com.nowcoder.community.content.application.result;

import java.util.List;
import java.util.UUID;

public record PostMediaViewResult(
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
    public PostMediaViewResult {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public record VideoSource(String url, String contentType, Integer width, Integer height) {
    }
}
