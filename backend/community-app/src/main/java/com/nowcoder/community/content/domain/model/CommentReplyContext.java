package com.nowcoder.community.content.domain.model;

import java.util.Objects;

public record CommentReplyContext(CommentSnapshot directParent, CommentSnapshot root) {

    public CommentReplyContext {
        Objects.requireNonNull(directParent, "directParent must not be null");
        Objects.requireNonNull(root, "root must not be null");
    }
}
