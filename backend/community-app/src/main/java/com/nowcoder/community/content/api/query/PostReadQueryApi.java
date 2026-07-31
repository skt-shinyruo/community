package com.nowcoder.community.content.api.query;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public interface PostReadQueryApi {

    List<PostSummaryView> listPostsByUser(UUID userId, Integer page, Integer size);

    List<RecentUserCommentView> listRecentCommentsByUser(UUID userId, Integer page, Integer size);

    record PostSummaryView(
            UUID id,
            UUID userId,
            String title,
            int type,
            int status,
            Date createTime,
            int commentCount,
            double score,
            UUID categoryId,
            List<String> tags,
            UUID lastReplyUserId,
            Date lastReplyTime,
            Date lastActivityTime,
            String lastReplyPreview
    ) {
    }

    record RecentUserCommentView(
            UUID id,
            UUID userId,
            int entityType,
            UUID entityId,
            UUID targetId,
            UUID postId,
            String postTitle,
            String content,
            Date createTime
    ) {
    }
}
