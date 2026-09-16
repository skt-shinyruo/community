package com.nowcoder.community.social.domain.repository;

import com.nowcoder.community.social.domain.model.LikeRelation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface LikeRepository {

    boolean addLike(LikeRelation relation);

    boolean removeLike(LikeRelation expectedRelation);

    /**
     * Allocates the next durable event version for one stable like relation.
     * The caller must hold an active transaction until its relation write and outbox insert commit.
     */
    long nextRelationEventVersion(UUID actorUserId, int entityType, UUID entityId);

    Optional<LikeRelation> findLike(UUID actorUserId, int entityType, UUID entityId);

    List<LikeRelation> scanLikesByEntity(int entityType, UUID entityId, UUID afterActorUserId, int limit);

    List<LikeRelation> scanCommentLikesByPost(
            UUID postId,
            UUID afterCommentId,
            UUID afterActorUserId,
            int limit
    );

    boolean isLiked(UUID userId, int entityType, UUID entityId);

    long countEntityLikes(int entityType, UUID entityId);

    long incrementUserLikeCount(UUID userId, long delta);

    long getUserLikeCount(UUID userId);

    Map<UUID, Long> countEntityLikesBatch(int entityType, List<UUID> entityIds);

    Map<UUID, Boolean> likedStatusesBatch(UUID userId, int entityType, List<UUID> entityIds);
}
