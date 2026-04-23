package com.nowcoder.community.user.service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public record UserProfilePageView(
        UUID userId,
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

    public record RecentCommentItemView(
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
