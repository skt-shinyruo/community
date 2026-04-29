package com.nowcoder.community.content.application.command;

import java.util.UUID;

public record CreateCommentCommand(
        UUID userId,
        UUID postId,
        Integer entityType,
        UUID entityId,
        UUID targetId,
        String content
) {
}
