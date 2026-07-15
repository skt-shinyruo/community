package com.nowcoder.community.content.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CommentDeletionResult(
        CommentTransitionStatus status,
        List<CommentSnapshot> deletedComments
) {

    public CommentDeletionResult {
        Objects.requireNonNull(status, "status must not be null");
        deletedComments = deletedComments == null ? List.of() : List.copyOf(deletedComments);
        if (status == CommentTransitionStatus.APPLIED && deletedComments.isEmpty()) {
            throw new IllegalArgumentException("APPLIED deletion must contain affected comments");
        }
        if (status != CommentTransitionStatus.APPLIED && !deletedComments.isEmpty()) {
            throw new IllegalArgumentException("non-APPLIED deletion must not contain affected comments");
        }
    }

    public boolean changed() {
        return status == CommentTransitionStatus.APPLIED;
    }

    public List<UUID> deletedCommentIds() {
        return deletedComments.stream()
                .map(CommentSnapshot::id)
                .toList();
    }

    public int deletedCount() {
        return deletedComments.size();
    }

    public static CommentDeletionResult applied(List<CommentSnapshot> deletedComments) {
        return new CommentDeletionResult(CommentTransitionStatus.APPLIED, deletedComments);
    }

    public static CommentDeletionResult noOp() {
        return new CommentDeletionResult(CommentTransitionStatus.NO_OP, List.of());
    }

    public static CommentDeletionResult stale() {
        return new CommentDeletionResult(CommentTransitionStatus.STALE, List.of());
    }

    public static CommentDeletionResult notFound() {
        return new CommentDeletionResult(CommentTransitionStatus.NOT_FOUND, List.of());
    }
}
