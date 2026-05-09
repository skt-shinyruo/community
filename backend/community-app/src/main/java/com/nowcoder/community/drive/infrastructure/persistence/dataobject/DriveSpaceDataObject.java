package com.nowcoder.community.drive.infrastructure.persistence.dataobject;

import com.nowcoder.community.drive.domain.model.DriveSpace;

import java.time.Instant;
import java.util.UUID;

public class DriveSpaceDataObject {

    private UUID spaceId;
    private UUID userId;
    private long quotaBytes;
    private long usedBytes;
    private Instant createdAt;
    private Instant updatedAt;

    public static DriveSpaceDataObject fromDomain(DriveSpace space) {
        DriveSpaceDataObject dataObject = new DriveSpaceDataObject();
        dataObject.setSpaceId(space.spaceId());
        dataObject.setUserId(space.userId());
        dataObject.setQuotaBytes(space.quotaBytes());
        dataObject.setUsedBytes(space.usedBytes());
        dataObject.setCreatedAt(space.createdAt());
        dataObject.setUpdatedAt(space.updatedAt());
        return dataObject;
    }

    public DriveSpace toDomain() {
        return new DriveSpace(spaceId, userId, quotaBytes, usedBytes, createdAt, updatedAt);
    }

    public UUID getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(UUID spaceId) {
        this.spaceId = spaceId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public long getQuotaBytes() {
        return quotaBytes;
    }

    public void setQuotaBytes(long quotaBytes) {
        this.quotaBytes = quotaBytes;
    }

    public long getUsedBytes() {
        return usedBytes;
    }

    public void setUsedBytes(long usedBytes) {
        this.usedBytes = usedBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
