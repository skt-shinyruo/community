package com.nowcoder.community.social.service;

import com.nowcoder.community.social.like.LikeService;
import com.nowcoder.community.social.like.dto.LikeRequest;
import com.nowcoder.community.social.like.dto.LikeResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LikeApplicationService {

    private final LikeService likeService;

    public LikeApplicationService(LikeService likeService) {
        this.likeService = likeService;
    }

    public LikeResponse setLike(UUID actorUserId, LikeRequest request) {
        return likeService.setLike(actorUserId, request);
    }

    public boolean isLiked(UUID actorUserId, int entityType, UUID entityId) {
        return likeService.isLiked(actorUserId, entityType, entityId);
    }

    public long count(int entityType, UUID entityId) {
        return likeService.count(entityType, entityId);
    }

    public Map<UUID, Long> counts(int entityType, List<UUID> entityIds) {
        return likeService.counts(entityType, entityIds);
    }

    public Map<UUID, Boolean> statuses(UUID actorUserId, int entityType, List<UUID> entityIds) {
        return likeService.statuses(actorUserId, entityType, entityIds);
    }

    public long userLikeCount(UUID userId) {
        return likeService.userLikeCount(userId);
    }
}
