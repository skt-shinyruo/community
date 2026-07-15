package com.nowcoder.community.content.infrastructure.persistence.dataobject;

import java.util.UUID;

public record CommentTransitionTargetDataObject(
        UUID commentId,
        long expectedVersion
) {
}
