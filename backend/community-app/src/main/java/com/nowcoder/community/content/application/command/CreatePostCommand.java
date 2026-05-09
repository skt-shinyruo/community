package com.nowcoder.community.content.application.command;

import java.util.List;
import java.util.UUID;

public record CreatePostCommand(
        UUID userId,
        String title,
        UUID categoryId,
        List<String> tags,
        List<PostContentBlockCommand> blocks
) {
    public CreatePostCommand {
        tags = tags == null ? List.of() : List.copyOf(tags);
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }
}
