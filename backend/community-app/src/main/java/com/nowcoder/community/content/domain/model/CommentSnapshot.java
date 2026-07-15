package com.nowcoder.community.content.domain.model;

import java.util.Date;
import java.util.Objects;
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
        int editCount,
        UUID deletedBy,
        String deletedReason,
        Date deletedTime,
        long version
) {
    public CommentSnapshot {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(postId, "postId must not be null");
        Objects.requireNonNull(rootCommentId, "rootCommentId must not be null");
        Objects.requireNonNull(createTime, "createTime must not be null");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        createTime = copy(createTime);
        updateTime = copy(updateTime);
        deletedTime = copy(deletedTime);
    }

    public boolean active() {
        return status == 0;
    }

    public boolean rootComment() {
        return parentCommentId == null;
    }

    @Override
    public Date createTime() {
        return copy(createTime);
    }

    @Override
    public Date updateTime() {
        return copy(updateTime);
    }

    @Override
    public Date deletedTime() {
        return copy(deletedTime);
    }

    private static Date copy(Date value) {
        return value == null ? null : new Date(value.getTime());
    }
}
