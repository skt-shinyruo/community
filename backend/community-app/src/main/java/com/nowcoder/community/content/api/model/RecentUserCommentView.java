package com.nowcoder.community.content.api.model;

import java.util.Date;

public record RecentUserCommentView(
        int id,
        int userId,
        int entityType,
        int entityId,
        int targetId,
        int postId,
        String postTitle,
        String content,
        Date createTime
) {
}
