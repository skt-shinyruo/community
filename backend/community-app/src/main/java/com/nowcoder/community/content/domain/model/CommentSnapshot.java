package com.nowcoder.community.content.domain.model;

import java.util.Date;
import java.util.UUID;

public record CommentSnapshot(
        UUID id,
        UUID userId,
        UUID postId,
        UUID rootCommentId,
        UUID parentCommentId,
        UUID replyToUserId,
        String content,
        int status,
        Date createTime,
        Date updateTime,
        int editCount
) {
    public boolean active() {
        return status == 0;
    }

    public boolean rootComment() {
        return parentCommentId == null;
    }
}
