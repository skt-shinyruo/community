package com.nowcoder.community.user.app.query;

import java.util.Date;
import java.util.List;

public record UserProfilePageView(
        int userId,
        String username,
        String headerUrl,
        int type,
        int status,
        Date createTime,
        int score,
        int level,
        long walletBalance,
        String walletStatus,
        boolean userLevelEnabled,
        Integer userLevel,
        Integer signInDaysInWindow,
        long likeCount,
        long followeeCount,
        long followerCount,
        boolean hasFollowed,
        boolean socialDegraded
) {

    public record RecentPostSummaryView(
            int id,
            int userId,
            String title,
            int type,
            int status,
            Date createTime,
            int commentCount,
            double score,
            Integer categoryId,
            List<String> tags,
            Integer lastReplyUserId,
            Date lastReplyTime,
            Date lastActivityTime,
            String lastReplyPreview
    ) {
    }

    public record RecentCommentItemView(
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
}
