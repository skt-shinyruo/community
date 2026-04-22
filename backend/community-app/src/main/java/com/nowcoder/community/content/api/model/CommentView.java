package com.nowcoder.community.content.api.model;

import java.util.Date;
import java.util.UUID;

public record CommentView(
        UUID id,
        UUID userId,
        int entityType,
        UUID entityId,
        UUID targetId,
        String content,
        Date createTime,
        Date updateTime,
        int editCount
) {
}
