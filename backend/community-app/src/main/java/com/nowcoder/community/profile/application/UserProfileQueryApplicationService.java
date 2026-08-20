package com.nowcoder.community.profile.application;

import com.nowcoder.community.content.api.query.PostReadQueryApi;
import com.nowcoder.community.growth.api.model.UserLevelSummaryView;
import com.nowcoder.community.growth.api.query.UserLevelQueryApi;
import com.nowcoder.community.profile.application.result.UserProfilePageResult;
import com.nowcoder.community.social.api.query.SocialFollowQueryApi;
import com.nowcoder.community.social.api.query.SocialLikeQueryApi;
import com.nowcoder.community.user.api.model.UserProfileView;
import com.nowcoder.community.user.api.query.UserProfileQueryApi;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.USER;

@Service
public class UserProfileQueryApplicationService {

    private final UserProfileQueryApi userProfileQueryApi;
    private final SocialLikeQueryApi socialLikeQueryApi;
    private final SocialFollowQueryApi socialFollowQueryApi;
    private final PostReadQueryApi postReadQueryApi;
    private final UserLevelQueryApi userLevelQueryApi;

    public UserProfileQueryApplicationService(
            UserProfileQueryApi userProfileQueryApi,
            SocialLikeQueryApi socialLikeQueryApi,
            SocialFollowQueryApi socialFollowQueryApi,
            PostReadQueryApi postReadQueryApi,
            UserLevelQueryApi userLevelQueryApi
    ) {
        this.userProfileQueryApi = userProfileQueryApi;
        this.socialLikeQueryApi = socialLikeQueryApi;
        this.socialFollowQueryApi = socialFollowQueryApi;
        this.postReadQueryApi = postReadQueryApi;
        this.userLevelQueryApi = userLevelQueryApi;
    }

    public UserProfilePageResult get(UUID userId) {
        UserProfileView user = userProfileQueryApi.getProfile(userId);
        UserLevelSummaryView levelSummary = userLevelQueryApi.evaluateLevel(userId);
        boolean userLevelEnabled = levelSummary != null && levelSummary.enabled();
        return new UserProfilePageResult(
                user.userId(),
                user.username(),
                user.headerUrl(),
                user.type(),
                user.status(),
                user.createTime(),
                userLevelEnabled,
                userLevelEnabled ? levelSummary.userLevel() : null,
                userLevelEnabled ? levelSummary.signInDaysInWindow() : null,
                userLikeCount(userId),
                followeeCount(userId),
                followerCount(userId)
        );
    }

    public List<PostReadQueryApi.PostSummaryView> listRecentPosts(UUID userId, Integer page, Integer size) {
        requireExistingUser(userId);
        return postReadQueryApi.listPostsByUser(userId, page, size);
    }

    public List<PostReadQueryApi.RecentUserCommentView> listRecentComments(UUID userId, Integer page, Integer size) {
        requireExistingUser(userId);
        return postReadQueryApi.listRecentCommentsByUser(userId, page, size);
    }

    private void requireExistingUser(UUID userId) {
        userProfileQueryApi.getProfile(userId);
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

}
