package com.nowcoder.community.user.app.query;

import com.nowcoder.community.content.api.model.PostSummaryView;
import com.nowcoder.community.content.api.model.RecentUserCommentView;
import com.nowcoder.community.content.api.query.PostReadQueryApi;
import com.nowcoder.community.growth.api.model.UserLevelSummaryView;
import com.nowcoder.community.growth.api.query.UserLevelQueryApi;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.user.api.model.UserProfileView;
import com.nowcoder.community.user.api.query.UserProfileQueryApi;
import com.nowcoder.community.user.service.UserSocialProfileService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class GetUserProfilePageQuery {

    private final UserProfileQueryApi userProfileQueryApi;
    private final UserSocialProfileService userSocialProfileService;
    private final PostReadQueryApi postReadQueryApi;
    private final UserLevelQueryApi userLevelQueryApi;

    public GetUserProfilePageQuery(
            UserProfileQueryApi userProfileQueryApi,
            UserSocialProfileService userSocialProfileService,
            PostReadQueryApi postReadQueryApi,
            UserLevelQueryApi userLevelQueryApi
    ) {
        this.userProfileQueryApi = userProfileQueryApi;
        this.userSocialProfileService = userSocialProfileService;
        this.postReadQueryApi = postReadQueryApi;
        this.userLevelQueryApi = userLevelQueryApi;
    }

    public UserProfilePageView get(Authentication authentication, UUID userId) {
        UserProfileView user = userProfileQueryApi.getProfile(userId);
        UUID viewerId = CurrentUser.tryUserUuid(authentication);
        boolean hasViewer = viewerId != null && !viewerId.equals(userId);
        UserSocialProfileService.UserProfileStats stats = UserSocialProfileService.UserProfileStats.empty();
        UserLevelSummaryView levelSummary = null;
        boolean userLevelEnabled = false;
        return new UserProfilePageView(
                user.userId(),
                user.username(),
                user.headerUrl(),
                user.type(),
                user.status(),
                user.createTime(),
                user.score(),
                user.level(),
                user.walletBalance(),
                user.walletStatus(),
                userLevelEnabled,
                userLevelEnabled ? levelSummary.userLevel() : null,
                userLevelEnabled ? levelSummary.signInDaysInWindow() : null,
                stats.getLikeCount(),
                stats.getFolloweeCount(),
                stats.getFollowerCount(),
                hasViewer && stats.isHasFollowed(),
                true
        );
    }

    public List<UserProfilePageView.RecentPostSummaryView> listRecentPosts(UUID userId, Integer page, Integer size) {
        userProfileQueryApi.getProfile(userId);
        return List.of();
    }

    public List<UserProfilePageView.RecentCommentItemView> listRecentComments(UUID userId, Integer page, Integer size) {
        userProfileQueryApi.getProfile(userId);
        return List.of();
    }

    private static UserProfilePageView.RecentPostSummaryView toRecentPostSummaryView(PostSummaryView view) {
        return new UserProfilePageView.RecentPostSummaryView(
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

    private static UserProfilePageView.RecentCommentItemView toRecentCommentItemView(RecentUserCommentView view) {
        return new UserProfilePageView.RecentCommentItemView(
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
