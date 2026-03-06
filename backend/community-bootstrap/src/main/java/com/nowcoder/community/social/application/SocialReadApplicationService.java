package com.nowcoder.community.social.application;

import com.nowcoder.community.contracts.domain.EntityTypes;
import com.nowcoder.community.social.application.dto.SocialUserProfileStats;
import com.nowcoder.community.social.follow.FollowService;
import com.nowcoder.community.social.like.LikeService;
import org.springframework.stereotype.Service;

@Service
public class SocialReadApplicationService {

    private static final int USER_ENTITY_TYPE = EntityTypes.USER;

    private final LikeService likeService;
    private final FollowService followService;

    public SocialReadApplicationService(LikeService likeService, FollowService followService) {
        this.likeService = likeService;
        this.followService = followService;
    }

    public long userLikeCount(int userId) {
        if (userId <= 0) {
            return 0L;
        }
        return likeService.userLikeCount(userId);
    }

    public long entityLikeCount(int entityType, int entityId) {
        if (!EntityTypes.isValid(entityType) || entityId <= 0) {
            return 0L;
        }
        return likeService.count(entityType, entityId);
    }

    public boolean hasLiked(int actorUserId, int entityType, int entityId) {
        if (actorUserId <= 0 || !EntityTypes.isValid(entityType) || entityId <= 0) {
            return false;
        }
        return likeService.isLiked(actorUserId, entityType, entityId);
    }

    public long followeeCount(int userId) {
        if (userId <= 0) {
            return 0L;
        }
        return followService.followeeCount(userId, USER_ENTITY_TYPE);
    }

    public long followerCount(int userId) {
        if (userId <= 0) {
            return 0L;
        }
        return followService.followerCount(USER_ENTITY_TYPE, userId);
    }

    public boolean hasFollowedUser(int actorUserId, int targetUserId) {
        if (actorUserId <= 0 || targetUserId <= 0 || actorUserId == targetUserId) {
            return false;
        }
        return followService.hasFollowed(actorUserId, USER_ENTITY_TYPE, targetUserId);
    }

    public SocialUserProfileStats userProfileStats(int userId, Integer viewerId) {
        if (userId <= 0) {
            return new SocialUserProfileStats();
        }

        SocialUserProfileStats response = new SocialUserProfileStats();
        response.setLikeCount(likeService.userLikeCount(userId));
        response.setFolloweeCount(followService.followeeCount(userId, USER_ENTITY_TYPE));
        response.setFollowerCount(followService.followerCount(USER_ENTITY_TYPE, userId));

        int viewer = viewerId == null ? 0 : viewerId;
        if (viewer > 0 && viewer != userId) {
            response.setHasFollowed(followService.hasFollowed(viewer, USER_ENTITY_TYPE, userId));
        }
        return response;
    }
}
