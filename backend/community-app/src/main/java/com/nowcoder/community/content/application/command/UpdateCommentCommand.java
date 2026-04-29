package com.nowcoder.community.content.application.command;

import java.util.UUID;

public record UpdateCommentCommand(
        UUID userId,
        UUID postId,
        UUID commentId,
        String content
) {
}
