package com.nowcoder.community.content.application.command;

import java.util.UUID;

public record CreateCommentCommand(
        UUID userId,
        UUID postId,
        UUID parentCommentId,
        UUID replyToUserId,
        String content
) {
}
