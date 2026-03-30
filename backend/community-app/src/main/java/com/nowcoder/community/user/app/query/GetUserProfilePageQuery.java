package com.nowcoder.community.user.app.query;

import com.nowcoder.community.content.api.model.PostSummaryView;
import com.nowcoder.community.content.api.model.RecentUserCommentView;
import com.nowcoder.community.content.api.query.PostReadQueryApi;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.user.api.model.UserProfileView;
import com.nowcoder.community.user.api.query.UserProfileQueryApi;
import com.nowcoder.community.user.service.UserSocialProfileService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetUserProfilePageQuery {

    private final UserProfileQueryApi userProfileQueryApi;
    private final UserSocialProfileService userSocialProfileService;
    private final PostReadQueryApi postReadQueryApi;

    public GetUserProfilePageQuery(
            UserProfileQueryApi userProfileQueryApi,
            UserSocialProfileService userSocialProfileService,
            PostReadQueryApi postReadQueryApi
    ) {
        this.userProfileQueryApi = userProfileQueryApi;
        this.userSocialProfileService = userSocialProfileService;
        this.postReadQueryApi = postReadQueryApi;
    }

    public UserProfilePageView get(Authentication authentication, int userId) {
        UserProfileView user = userProfileQueryApi.getProfile(userId);

        Integer maybeCurrentUserId = CurrentUser.tryUserId(authentication);
        int currentUserId = maybeCurrentUserId == null ? 0 : maybeCurrentUserId;
        int viewerId = (currentUserId > 0 && currentUserId != userId) ? currentUserId : 0;

        UserSocialProfileService.UserProfileStats stats = userSocialProfileService.userProfileStats(userId, viewerId);
        return new UserProfilePageView(
                user.userId(),
                user.username(),
                user.headerUrl(),
                user.type(),
                user.status(),
                user.createTime(),
                user.score(),
                user.level(),
                stats.getLikeCount(),
                stats.getFolloweeCount(),
                stats.getFollowerCount(),
                viewerId > 0 && stats.isHasFollowed(),
                stats.isDegraded()
        );
    }

    public List<UserProfilePageView.RecentPostSummaryView> listRecentPosts(int userId, Integer page, Integer size) {
        userProfileQueryApi.getProfile(userId);
        return postReadQueryApi.listPostsByUser(userId, page, size).stream()
                .map(GetUserProfilePageQuery::toRecentPostSummaryView)
                .toList();
    }

    public List<UserProfilePageView.RecentCommentItemView> listRecentComments(int userId, Integer page, Integer size) {
        userProfileQueryApi.getProfile(userId);
        return postReadQueryApi.listRecentCommentsByUser(userId, page, size).stream()
                .map(GetUserProfilePageQuery::toRecentCommentItemView)
                .toList();
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
