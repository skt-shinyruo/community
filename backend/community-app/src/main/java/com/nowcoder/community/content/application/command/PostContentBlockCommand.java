package com.nowcoder.community.content.application.command;

import com.nowcoder.community.content.domain.service.PostContentBlockPolicy.ContentBlockCommand;

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
) implements ContentBlockCommand<PostContentBlockCommand> {

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
