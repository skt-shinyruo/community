package com.nowcoder.community.social.domain.repository;

import com.nowcoder.community.social.domain.model.FollowRelation;

import java.util.List;
import java.util.UUID;

public interface FollowRepository {

    boolean follow(UUID userId, int entityType, UUID entityId, long followTimeMillis);

    boolean unfollow(UUID userId, int entityType, UUID entityId);

    boolean hasFollowed(UUID userId, int entityType, UUID entityId);

    long countFollowees(UUID userId, int entityType);

    long countFollowers(int entityType, UUID entityId);

    default long countFolloweesExcludingBlocked(UUID userId, int entityType, BlockRepository blockRepository) {
        return listFollowees(userId, entityType, 0, Integer.MAX_VALUE).stream()
                .filter(relation -> relation != null && !isEitherBlocked(userId, relation.targetId(), blockRepository))
                .count();
    }

    default long countFollowersExcludingBlocked(int entityType, UUID entityId, BlockRepository blockRepository) {
        return listFollowers(entityType, entityId, 0, Integer.MAX_VALUE).stream()
                .filter(relation -> relation != null && !isEitherBlocked(entityId, relation.targetId(), blockRepository))
                .count();
    }

    List<FollowRelation> listFollowees(UUID userId, int entityType, int offset, int limit);

    List<FollowRelation> listFollowers(int entityType, UUID entityId, int offset, int limit);

    default List<FollowRelation> listFolloweesExcludingBlocked(
            UUID userId,
            int entityType,
            BlockRepository blockRepository,
            int offset,
            int limit
    ) {
        return listFollowees(userId, entityType, 0, Integer.MAX_VALUE).stream()
                .filter(relation -> relation != null && !isEitherBlocked(userId, relation.targetId(), blockRepository))
                .skip(Math.max(0, offset))
                .limit(Math.max(0, limit))
                .toList();
    }

    default List<FollowRelation> listFollowersExcludingBlocked(
            int entityType,
            UUID entityId,
            BlockRepository blockRepository,
            int offset,
            int limit
    ) {
        return listFollowers(entityType, entityId, 0, Integer.MAX_VALUE).stream()
                .filter(relation -> relation != null && !isEitherBlocked(entityId, relation.targetId(), blockRepository))
                .skip(Math.max(0, offset))
                .limit(Math.max(0, limit))
                .toList();
    }

    private boolean isEitherBlocked(UUID userIdA, UUID userIdB, BlockRepository blockRepository) {
        if (userIdA == null || userIdB == null || userIdA.equals(userIdB) || blockRepository == null) {
            return false;
        }
        return blockRepository.hasBlocked(userIdA, userIdB) || blockRepository.hasBlocked(userIdB, userIdA);
    }

    default boolean requiresExplicitCompensation() {
        return false;
    }
}
