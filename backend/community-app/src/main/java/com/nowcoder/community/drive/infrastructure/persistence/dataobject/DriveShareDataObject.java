package com.nowcoder.community.drive.infrastructure.persistence.dataobject;

import com.nowcoder.community.drive.domain.model.DriveShare;
import com.nowcoder.community.drive.domain.model.DriveShareStatus;

import java.time.Instant;
import java.util.UUID;

public class DriveShareDataObject {

    private UUID shareId;
    private UUID entryId;
    private String shareToken;
    private String passwordHash;
    private Instant expiresAt;
    private DriveShareStatus status;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public static DriveShareDataObject fromDomain(DriveShare share) {
        DriveShareDataObject dataObject = new DriveShareDataObject();
        dataObject.setShareId(share.shareId());
        dataObject.setEntryId(share.entryId());
        dataObject.setShareToken(share.shareToken());
        dataObject.setPasswordHash(share.passwordHash());
        dataObject.setExpiresAt(share.expiresAt());
        dataObject.setStatus(share.status());
        dataObject.setCreatedBy(share.createdBy());
        dataObject.setCreatedAt(share.createdAt());
        dataObject.setUpdatedAt(share.revokedAt() == null ? share.createdAt() : share.revokedAt());
        return dataObject;
    }

    public DriveShare toDomain() {
        Instant revokedAt = status == DriveShareStatus.REVOKED ? updatedAt : null;
        return new DriveShare(
                shareId,
                entryId,
                shareToken,
                passwordHash,
                expiresAt,
                createdBy,
                status,
                createdAt,
                revokedAt
        );
    }

    public UUID getShareId() {
        return shareId;
    }

    public void setShareId(UUID shareId) {
        this.shareId = shareId;
    }

    public UUID getEntryId() {
        return entryId;
    }

    public void setEntryId(UUID entryId) {
        this.entryId = entryId;
    }

    public String getShareToken() {
        return shareToken;
    }

    public void setShareToken(String shareToken) {
        this.shareToken = shareToken;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public DriveShareStatus getStatus() {
        return status;
    }

    public void setStatus(DriveShareStatus status) {
        this.status = status;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
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
