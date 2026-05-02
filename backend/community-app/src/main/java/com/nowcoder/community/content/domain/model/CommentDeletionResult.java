package com.nowcoder.community.content.domain.model;

import java.util.List;
import java.util.UUID;

public record CommentDeletionResult(List<CommentSnapshot> deletedComments) {

    public CommentDeletionResult {
        deletedComments = deletedComments == null ? List.of() : List.copyOf(deletedComments);
    }

    public boolean changed() {
        return !deletedComments.isEmpty();
    }

    public List<UUID> deletedCommentIds() {
        return deletedComments.stream()
                .map(CommentSnapshot::id)
                .toList();
    }

    public int deletedCount() {
        return deletedComments.size();
    }
}
