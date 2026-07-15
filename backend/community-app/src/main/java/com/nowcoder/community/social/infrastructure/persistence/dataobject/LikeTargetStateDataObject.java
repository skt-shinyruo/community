package com.nowcoder.community.social.infrastructure.persistence.dataobject;

import java.time.Instant;
import java.util.UUID;

public class LikeTargetStateDataObject {

    private int entityType;
    private UUID entityId;
    private String status;
    private String sourceEventId;
    private long sourceVersion;
    private Instant deletedAt;

    public int getEntityType() {
        return entityType;
    }

    public void setEntityType(int entityType) {
        this.entityType = entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSourceEventId() {
        return sourceEventId;
    }

    public void setSourceEventId(String sourceEventId) {
        this.sourceEventId = sourceEventId;
    }

    public long getSourceVersion() {
        return sourceVersion;
    }

    public void setSourceVersion(long sourceVersion) {
        this.sourceVersion = sourceVersion;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
