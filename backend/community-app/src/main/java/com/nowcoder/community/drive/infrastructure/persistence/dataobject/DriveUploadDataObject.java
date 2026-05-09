package com.nowcoder.community.drive.infrastructure.persistence.dataobject;

import com.nowcoder.community.drive.domain.model.DriveUpload;
import com.nowcoder.community.drive.domain.model.DriveUploadStatus;

import java.time.Instant;
import java.util.UUID;

public class DriveUploadDataObject {

    private UUID uploadId;
    private UUID spaceId;
    private UUID parentId;
    private String name;
    private long sizeBytes;
    private String mimeType;
    private UUID objectId;
    private UUID versionId;
    private UUID ossSessionId;
    private DriveUploadStatus status;
    private UUID createdBy;
    private Instant createdAt;
    private Instant expiresAt;
    private UUID completedEntryId;

    public static DriveUploadDataObject fromDomain(DriveUpload upload) {
        DriveUploadDataObject dataObject = new DriveUploadDataObject();
        dataObject.setUploadId(upload.uploadId());
        dataObject.setSpaceId(upload.spaceId());
        dataObject.setParentId(upload.parentId());
        dataObject.setName(upload.name());
        dataObject.setSizeBytes(upload.sizeBytes());
        dataObject.setMimeType(upload.mimeType());
        dataObject.setObjectId(upload.objectId());
        dataObject.setVersionId(upload.versionId());
        dataObject.setOssSessionId(upload.ossSessionId());
        dataObject.setStatus(upload.status());
        dataObject.setCreatedBy(upload.createdBy());
        dataObject.setCreatedAt(upload.createdAt());
        dataObject.setExpiresAt(upload.expiresAt());
        dataObject.setCompletedEntryId(upload.completedEntryId());
        return dataObject;
    }

    public DriveUpload toDomain() {
        return new DriveUpload(
                uploadId,
                spaceId,
                parentId,
                name,
                sizeBytes,
                mimeType,
                objectId,
                versionId,
                ossSessionId,
                createdBy,
                status,
                completedEntryId,
                createdAt,
                createdAt,
                expiresAt,
                null
        );
    }

    public UUID getUploadId() {
        return uploadId;
    }

    public void setUploadId(UUID uploadId) {
        this.uploadId = uploadId;
    }

    public UUID getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(UUID spaceId) {
        this.spaceId = spaceId;
    }

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public UUID getObjectId() {
        return objectId;
    }

    public void setObjectId(UUID objectId) {
        this.objectId = objectId;
    }

    public UUID getVersionId() {
        return versionId;
    }

    public void setVersionId(UUID versionId) {
        this.versionId = versionId;
    }

    public UUID getOssSessionId() {
        return ossSessionId;
    }

    public void setOssSessionId(UUID ossSessionId) {
        this.ossSessionId = ossSessionId;
    }

    public DriveUploadStatus getStatus() {
        return status;
    }

    public void setStatus(DriveUploadStatus status) {
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

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public UUID getCompletedEntryId() {
        return completedEntryId;
    }

    public void setCompletedEntryId(UUID completedEntryId) {
        this.completedEntryId = completedEntryId;
    }
}
