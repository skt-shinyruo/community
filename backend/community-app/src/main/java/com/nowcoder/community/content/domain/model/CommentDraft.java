package com.nowcoder.community.content.domain.model;

import java.util.Date;
import java.util.UUID;

public record CommentDraft(
        UUID userId,
        int entityType,
        UUID entityId,
        UUID targetId,
        String content,
        Date createTime
) {
}
