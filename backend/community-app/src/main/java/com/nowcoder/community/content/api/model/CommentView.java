package com.nowcoder.community.content.api.model;

import java.util.Date;

public record CommentView(
        int id,
        int userId,
        int entityType,
        int entityId,
        int targetId,
        String content,
        Date createTime,
        Date updateTime,
        int editCount
) {
}
