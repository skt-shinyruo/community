package com.nowcoder.community.content.domain.model;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

public record CommentEdit(
        UUID commentId,
        long expectedVersion,
        String content,
        Date updateTime
) {
    public CommentEdit {
        Objects.requireNonNull(commentId, "commentId must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(updateTime, "updateTime must not be null");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
        updateTime = new Date(updateTime.getTime());
    }

    @Override
    public Date updateTime() {
        return new Date(updateTime.getTime());
    }
}
