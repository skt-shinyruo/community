package com.nowcoder.community.content.application.command;

import java.util.List;
import java.util.UUID;

public record CreatePostCommand(
        UUID userId,
        String title,
        String content,
        UUID categoryId,
        List<String> tags
) {
}
