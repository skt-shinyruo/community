package com.nowcoder.community.social.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class LikeTargetState {

    public enum Status {
        ACTIVE,
        DELETED
    }

    private final int entityType;
    private final UUID entityId;
    private final Status status;
    private final String sourceEventId;
    private final long sourceVersion;
    private final Instant deletedAt;

    private LikeTargetState(
            int entityType,
            UUID entityId,
            Status status,
            String sourceEventId,
            long sourceVersion,
            Instant deletedAt
    ) {
        this.entityType = entityType;
        this.entityId = Objects.requireNonNull(entityId, "entityId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.sourceEventId = sourceEventId;
        this.sourceVersion = sourceVersion;
        this.deletedAt = deletedAt;
    }

    public static LikeTargetState active(int entityType, UUID entityId) {
        return new LikeTargetState(entityType, entityId, Status.ACTIVE, null, 0L, null);
    }

    public static LikeTargetState restore(
            int entityType,
            UUID entityId,
            String status,
            String sourceEventId,
            long sourceVersion,
            Instant deletedAt
    ) {
        Status restoredStatus = Status.valueOf(Objects.requireNonNull(status, "status must not be null"));
        if (restoredStatus == Status.ACTIVE) {
            return active(entityType, entityId);
        }
        if (sourceEventId == null || sourceEventId.isBlank() || sourceVersion <= 0L || deletedAt == null) {
            throw new IllegalArgumentException("deleted like target state is incomplete");
        }
        return new LikeTargetState(
                entityType,
                entityId,
                Status.DELETED,
                sourceEventId.trim(),
                sourceVersion,
                deletedAt
        );
    }

    public LikeTargetState applyDeletion(String eventId, long version, Instant occurredAt) {
        if (eventId == null || eventId.isBlank() || version <= 0L || occurredAt == null) {
            throw new IllegalArgumentException("deletion fact is incomplete");
        }
        if (version <= sourceVersion) {
            return this;
        }
        return new LikeTargetState(
                entityType,
                entityId,
                Status.DELETED,
                eventId.trim(),
                version,
                occurredAt
        );
    }

    public int entityType() {
        return entityType;
    }

    public UUID entityId() {
        return entityId;
    }

    public Status status() {
        return status;
    }

    public String sourceEventId() {
        return sourceEventId;
    }

    public long sourceVersion() {
        return sourceVersion;
    }

    public Instant deletedAt() {
        return deletedAt;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean isDeleted() {
        return status == Status.DELETED;
    }
}
