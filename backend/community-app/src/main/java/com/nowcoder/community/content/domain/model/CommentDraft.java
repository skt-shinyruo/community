package com.nowcoder.community.content.domain.model;

import java.util.Date;
import java.util.UUID;

public record CommentDraft(
        UUID userId,
        UUID postId,
        UUID rootCommentId,
        UUID parentCommentId,
        UUID replyToUserId,
        String content,
        Date createTime
) {
}
