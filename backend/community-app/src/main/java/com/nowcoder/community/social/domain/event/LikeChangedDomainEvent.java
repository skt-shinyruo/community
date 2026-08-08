package com.nowcoder.community.social.domain.event;

import java.time.Instant;
import java.util.UUID;

public record LikeChangedDomainEvent(
        UUID actorUserId,
        int entityType,
        UUID entityId,
        UUID entityUserId,
        UUID postId,
        String relationKey,
        UUID relationInstanceId,
        long relationVersion,
        boolean liked,
        Instant occurredAt
) {

    public LikeChangedDomainEvent(
            UUID actorUserId,
            int entityType,
            UUID entityId,
            UUID entityUserId,
            UUID postId,
            String relationKey,
            UUID relationInstanceId,
            boolean liked,
            Instant occurredAt
    ) {
        this(
                actorUserId,
                entityType,
                entityId,
                entityUserId,
                postId,
                relationKey,
                relationInstanceId,
                legacyVersion(occurredAt),
                liked,
                occurredAt
        );
    }

    public LikeChangedDomainEvent(
            UUID actorUserId,
            int entityType,
            UUID entityId,
            UUID entityUserId,
            UUID postId,
            String relationKey,
            boolean liked,
            Instant occurredAt
    ) {
        this(actorUserId, entityType, entityId, entityUserId, postId, relationKey, null, liked, occurredAt);
    }

    public LikeChangedDomainEvent(
            UUID actorUserId,
            int entityType,
            UUID entityId,
            UUID entityUserId,
            UUID postId,
            String relationKey,
            long relationVersion,
            boolean liked,
            Instant occurredAt
    ) {
        this(actorUserId, entityType, entityId, entityUserId, postId, relationKey, null, relationVersion, liked, occurredAt);
    }

    private static long legacyVersion(Instant occurredAt) {
        return occurredAt == null ? 1L : Math.max(1L, occurredAt.toEpochMilli());
    }
}
