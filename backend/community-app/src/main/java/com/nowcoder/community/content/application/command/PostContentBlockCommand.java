package com.nowcoder.community.content.application.command;

import com.nowcoder.community.content.domain.model.PostContentBlockCommandSpec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record PostContentBlockCommand(
        String type,
        String text,
        UUID assetId,
        String language,
        String caption,
        String displayName,
        Map<String, Object> metadata
) implements PostContentBlockCommandSpec<PostContentBlockCommand> {

    public PostContentBlockCommand {
        metadata = copyMetadata(metadata);
    }

    private static Map<String, Object> copyMetadata(Map<String, Object> metadata) {
        return metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    @Override
    public PostContentBlockCommand normalized(
            String type,
            String text,
            UUID assetId,
            String language,
            String caption,
            String displayName,
            Map<String, Object> metadata
    ) {
        return new PostContentBlockCommand(type, text, assetId, language, caption, displayName, metadata);
    }
}
