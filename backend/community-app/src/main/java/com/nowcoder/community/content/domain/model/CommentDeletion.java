package com.nowcoder.community.content.domain.model;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

public record CommentDeletion(
        UUID commentId,
        long expectedVersion,
        UUID deletedBy,
        String deletedReason,
        Date deletedTime
) {
    public CommentDeletion {
        Objects.requireNonNull(commentId, "commentId must not be null");
        Objects.requireNonNull(deletedBy, "deletedBy must not be null");
        Objects.requireNonNull(deletedTime, "deletedTime must not be null");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
        deletedTime = new Date(deletedTime.getTime());
    }

    @Override
    public Date deletedTime() {
        return new Date(deletedTime.getTime());
    }
}
