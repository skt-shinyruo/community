package com.nowcoder.community.content.application.result;

import java.util.Date;
import java.util.UUID;

public record CommentResult(
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
