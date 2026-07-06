package com.nowcoder.community.content.application.result;

import java.util.Date;
import java.util.UUID;

public record CommentResult(
        UUID id,
        UUID userId,
        UUID postId,
        UUID rootCommentId,
        UUID parentCommentId,
        UUID replyToUserId,
        String content,
        Date createTime,
        Date updateTime,
        int editCount
) {
}
