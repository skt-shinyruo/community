package com.nowcoder.community.content.domain.model;

import java.util.Map;
import java.util.UUID;

public interface PostContentBlockCommandSpec<T extends PostContentBlockCommandSpec<T>> {

    String type();

    String text();

    UUID assetId();

    String language();

    String caption();

    String displayName();

    Map<String, Object> metadata();

    T normalized(
            String type,
            String text,
            UUID assetId,
            String language,
            String caption,
            String displayName,
            Map<String, Object> metadata
    );
}
