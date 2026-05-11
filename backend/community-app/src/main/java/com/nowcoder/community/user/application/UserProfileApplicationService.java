package com.nowcoder.community.user.application;

import com.nowcoder.community.content.api.model.PostSummaryView;
import com.nowcoder.community.content.api.model.RecentUserCommentView;
import com.nowcoder.community.content.api.query.PostReadQueryApi;
import com.nowcoder.community.growth.api.model.UserLevelSummaryView;
import com.nowcoder.community.growth.api.query.UserLevelQueryApi;
import com.nowcoder.community.social.api.query.SocialFollowQueryApi;
import com.nowcoder.community.social.api.query.SocialLikeQueryApi;
import com.nowcoder.community.user.application.result.UserProfilePageResult;
import com.nowcoder.community.user.application.result.UserProfileResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.USER;

@Service
public class UserProfileApplicationService {

    private final UserReadApplicationService userReadApplicationService;
    private final SocialLikeQueryApi socialLikeQueryApi;
    private final SocialFollowQueryApi socialFollowQueryApi;
    private final PostReadQueryApi postReadQueryApi;
    private final UserLevelQueryApi userLevelQueryApi;

    public UserProfileApplicationService(
            UserReadApplicationService userReadApplicationService,
            SocialLikeQueryApi socialLikeQueryApi,
            SocialFollowQueryApi socialFollowQueryApi,
            PostReadQueryApi postReadQueryApi,
            UserLevelQueryApi userLevelQueryApi
    ) {
        this.userReadApplicationService = userReadApplicationService;
        this.socialLikeQueryApi = socialLikeQueryApi;
        this.socialFollowQueryApi = socialFollowQueryApi;
        this.postReadQueryApi = postReadQueryApi;
        this.userLevelQueryApi = userLevelQueryApi;
    }

    public UserProfilePageResult get(UUID viewerId, UUID userId) {
        UserProfileResult user = userReadApplicationService.getProfile(userId);
        UserLevelSummaryView levelSummary = userLevelQueryApi.evaluateLevel(userId);
        boolean userLevelEnabled = levelSummary != null && levelSummary.enabled();
        return new UserProfilePageResult(
                user.userId(),
                user.username(),
                user.headerUrl(),
                user.type(),
                user.status(),
                user.createTime(),
                user.score(),
                user.level(),
                userLevelEnabled,
                userLevelEnabled ? levelSummary.userLevel() : null,
                userLevelEnabled ? levelSummary.signInDaysInWindow() : null,
                userLikeCount(userId),
                followeeCount(userId),
                followerCount(userId),
                hasFollowed(viewerId, userId)
        );
    }

    public List<UserProfilePageResult.RecentPostSummaryResult> listRecentPosts(UUID userId, Integer page, Integer size) {
        userReadApplicationService.requireExistingUser(userId);
        return postReadQueryApi.listPostsByUser(userId, page, size).stream()
                .map(UserProfileApplicationService::toRecentPostSummaryResult)
                .toList();
    }

    public List<UserProfilePageResult.RecentCommentItemResult> listRecentComments(UUID userId, Integer page, Integer size) {
        userReadApplicationService.requireExistingUser(userId);
        return postReadQueryApi.listRecentCommentsByUser(userId, page, size).stream()
                .map(UserProfileApplicationService::toRecentCommentItemResult)
                .toList();
    }

    private long userLikeCount(UUID userId) {
        return userId == null ? 0L : socialLikeQueryApi.userLikeCount(userId);
    }

    private long followeeCount(UUID userId) {
        return userId == null ? 0L : socialFollowQueryApi.followeeCount(userId, USER);
    }

    private long followerCount(UUID userId) {
        return userId == null ? 0L : socialFollowQueryApi.followerCount(USER, userId);
    }

    private boolean hasFollowed(UUID viewerId, UUID userId) {
        if (viewerId == null || userId == null || viewerId.equals(userId)) {
            return false;
        }
        return socialFollowQueryApi.hasFollowed(viewerId, USER, userId);
    }

    private static UserProfilePageResult.RecentPostSummaryResult toRecentPostSummaryResult(PostSummaryView view) {
        return new UserProfilePageResult.RecentPostSummaryResult(
                view.id(),
                view.userId(),
                view.title(),
                view.type(),
                view.status(),
                view.createTime(),
                view.commentCount(),
                view.score(),
                view.categoryId(),
                view.tags(),
                view.lastReplyUserId(),
                view.lastReplyTime(),
                view.lastActivityTime(),
                view.lastReplyPreview()
        );
    }

    private static UserProfilePageResult.RecentCommentItemResult toRecentCommentItemResult(RecentUserCommentView view) {
        return new UserProfilePageResult.RecentCommentItemResult(
                view.id(),
                view.userId(),
                view.entityType(),
                view.entityId(),
                view.targetId(),
                view.postId(),
                view.postTitle(),
                view.content(),
                view.createTime()
        );
    }
}
