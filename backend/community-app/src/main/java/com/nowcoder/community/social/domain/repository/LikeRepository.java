package com.nowcoder.community.social.domain.repository;

import com.nowcoder.community.social.domain.model.LikeRelation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface LikeRepository {

    boolean addLike(LikeRelation relation);

    boolean removeLike(LikeRelation expectedRelation);

    Optional<LikeRelation> findLike(UUID actorUserId, int entityType, UUID entityId);

    long deleteLikesByEntity(int entityType, UUID entityId);

    List<LikeRelation> scanLikesByEntity(int entityType, UUID entityId, UUID afterActorUserId, int limit);

    List<UUID> scanTargetIdsAfter(int entityType, UUID afterEntityId, int limit);

    boolean isLiked(UUID userId, int entityType, UUID entityId);

    long countEntityLikes(int entityType, UUID entityId);

    long incrementUserLikeCount(UUID userId, long delta);

    default long resetUserLikeCount(UUID userId, long likeCount) {
        long current = getUserLikeCount(userId);
        return incrementUserLikeCount(userId, Math.max(0, likeCount) - current);
    }

    long getUserLikeCount(UUID userId);

    default Map<UUID, Long> countEntityLikesBatch(int entityType, List<UUID> entityIds) {
        Map<UUID, Long> out = new HashMap<>();
        if (entityIds == null || entityIds.isEmpty()) {
            return out;
        }
        for (UUID id : entityIds) {
            if (id == null) {
                continue;
            }
            out.put(id, countEntityLikes(entityType, id));
        }
        return out;
    }

    default Map<UUID, Boolean> likedStatusesBatch(UUID userId, int entityType, List<UUID> entityIds) {
        Map<UUID, Boolean> out = new HashMap<>();
        if (entityIds == null || entityIds.isEmpty()) {
            return out;
        }
        for (UUID id : entityIds) {
            if (id == null) {
                continue;
            }
            out.put(id, isLiked(userId, entityType, id));
        }
        return out;
    }
}
