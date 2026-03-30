package com.nowcoder.community.user.service;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.social.api.query.SocialFollowQueryApi;
import com.nowcoder.community.social.api.query.SocialLikeQueryApi;
import org.springframework.stereotype.Service;

@Service
public class UserSocialProfileService {

    private static final int USER_ENTITY_TYPE = EntityTypes.USER;

    private final SocialLikeQueryApi likeQueryApi;
    private final SocialFollowQueryApi followQueryApi;

    public UserSocialProfileService(
            SocialLikeQueryApi likeQueryApi,
            SocialFollowQueryApi followQueryApi
    ) {
        this.likeQueryApi = likeQueryApi;
        this.followQueryApi = followQueryApi;
    }

    public UserProfileStats userProfileStats(int userId, int viewerId) {
        if (userId <= 0) {
            return UserProfileStats.empty();
        }

        UserProfileStats response = new UserProfileStats();
        response.setLikeCount(userLikeCount(userId));
        response.setFolloweeCount(followeeCount(userId));
        response.setFollowerCount(followerCount(userId));

        if (viewerId > 0 && viewerId != userId) {
            response.setHasFollowed(hasFollowed(viewerId, userId));
        }
        return response;
    }

    public long userLikeCount(int userId) {
        if (userId <= 0) {
            return 0L;
        }
        return likeQueryApi.userLikeCount(userId);
    }

    public long followeeCount(int userId) {
        if (userId <= 0) {
            return 0L;
        }
        return followQueryApi.followeeCount(userId, USER_ENTITY_TYPE);
    }

    public long followerCount(int userId) {
        if (userId <= 0) {
            return 0L;
        }
        return followQueryApi.followerCount(USER_ENTITY_TYPE, userId);
    }

    public boolean hasFollowed(int actorUserId, int targetUserId) {
        if (actorUserId <= 0 || targetUserId <= 0 || actorUserId == targetUserId) {
            return false;
        }
        return followQueryApi.hasFollowed(actorUserId, USER_ENTITY_TYPE, targetUserId);
    }

    public static class UserProfileStats {

        private long likeCount;
        private long followeeCount;
        private long followerCount;
        private boolean hasFollowed;
        private boolean degraded;

        public static UserProfileStats empty() {
            return new UserProfileStats();
        }

        public long getLikeCount() {
            return likeCount;
        }

        public void setLikeCount(long likeCount) {
            this.likeCount = likeCount;
        }

        public long getFolloweeCount() {
            return followeeCount;
        }

        public void setFolloweeCount(long followeeCount) {
            this.followeeCount = followeeCount;
        }

        public long getFollowerCount() {
            return followerCount;
        }

        public void setFollowerCount(long followerCount) {
            this.followerCount = followerCount;
        }

        public boolean isHasFollowed() {
            return hasFollowed;
        }

        public void setHasFollowed(boolean hasFollowed) {
            this.hasFollowed = hasFollowed;
        }

        public boolean isDegraded() {
            return degraded;
        }

        public void setDegraded(boolean degraded) {
            this.degraded = degraded;
        }
    }
}
