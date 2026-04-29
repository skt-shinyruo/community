package com.nowcoder.community.content.domain.model;

import java.util.Date;
import java.util.UUID;

public record CommentSnapshot(
        UUID id,
        UUID userId,
        int entityType,
        UUID entityId,
        UUID targetId,
        String content,
        int status,
        Date createTime,
        Date updateTime,
        int editCount
) {
    public boolean active() {
        return status == 0;
    }
}
