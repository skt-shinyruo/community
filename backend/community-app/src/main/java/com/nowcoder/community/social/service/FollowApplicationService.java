package com.nowcoder.community.social.service;

import com.nowcoder.community.social.follow.FollowService;
import com.nowcoder.community.social.follow.dto.FollowItem;
import com.nowcoder.community.social.follow.dto.FollowRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class FollowApplicationService {

    private final FollowService followService;

    public FollowApplicationService(FollowService followService) {
        this.followService = followService;
    }

    public void follow(UUID actorUserId, FollowRequest request) {
        followService.follow(actorUserId, request);
    }

    public void unfollow(UUID actorUserId, int entityType, UUID entityId) {
        followService.unfollow(actorUserId, entityType, entityId);
    }

    public boolean hasFollowed(UUID actorUserId, int entityType, UUID entityId) {
        return followService.hasFollowed(actorUserId, entityType, entityId);
    }

    public List<FollowItem> listFollowees(UUID userId, int entityType, int page, int size) {
        return followService.listFollowees(userId, entityType, page, size);
    }

    public List<FollowItem> listFollowers(int entityType, UUID entityId, int page, int size) {
        return followService.listFollowers(entityType, entityId, page, size);
    }

    public long followeeCount(UUID userId, int entityType) {
        return followService.followeeCount(userId, entityType);
    }

    public long followerCount(int entityType, UUID entityId) {
        return followService.followerCount(entityType, entityId);
    }
}
