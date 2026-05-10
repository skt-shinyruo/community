package com.nowcoder.community.drive.infrastructure.persistence.dataobject;

import com.nowcoder.community.drive.domain.model.DriveEntry;
import com.nowcoder.community.drive.domain.model.DriveEntryStatus;
import com.nowcoder.community.drive.domain.model.DriveEntryType;

import java.time.Instant;
import java.util.UUID;

public class DriveEntryDataObject {

    private UUID entryId;
    private UUID spaceId;
    private UUID parentId;
    private String parentKey;
    private String activeName;
    private DriveEntryType type;
    private String name;
    private UUID objectId;
    private UUID versionId;
    private long sizeBytes;
    private String mimeType;
    private DriveEntryStatus status;
    private Instant trashedAt;
    private Instant deleteAfter;
    private UUID trashRootId;
    private Instant createdAt;
    private Instant updatedAt;

    public static DriveEntryDataObject fromDomain(DriveEntry entry) {
        DriveEntryDataObject dataObject = new DriveEntryDataObject();
        dataObject.setEntryId(entry.entryId());
        dataObject.setSpaceId(entry.spaceId());
        dataObject.setParentId(entry.parentId());
        dataObject.setParentKey(parentKey(entry.parentId()));
        dataObject.setActiveName(entry.status() == DriveEntryStatus.ACTIVE ? entry.name() : null);
        dataObject.setType(entry.type());
        dataObject.setName(entry.name());
        dataObject.setObjectId(entry.objectId());
        dataObject.setVersionId(entry.versionId());
        dataObject.setSizeBytes(entry.sizeBytes());
        dataObject.setMimeType(entry.mimeType() == null ? "" : entry.mimeType());
        dataObject.setStatus(entry.status());
        dataObject.setTrashedAt(entry.trashedAt());
        dataObject.setDeleteAfter(entry.deleteAfter());
        dataObject.setTrashRootId(entry.trashRootId());
        dataObject.setCreatedAt(entry.createdAt());
        dataObject.setUpdatedAt(entry.updatedAt());
        return dataObject;
    }

    public DriveEntry toDomain() {
        return new DriveEntry(
                entryId,
                spaceId,
                parentId,
                type,
                status,
                name,
                objectId,
                versionId,
                sizeBytes,
                emptyToNull(mimeType),
                trashedAt,
                deleteAfter,
                trashRootId,
                createdAt,
                updatedAt
        );
    }

    public static String parentKey(UUID parentId) {
        return parentId == null ? "" : parentId.toString().replace("-", "");
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    public UUID getEntryId() {
        return entryId;
    }

    public void setEntryId(UUID entryId) {
        this.entryId = entryId;
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

    public String getParentKey() {
        return parentKey;
    }

    public void setParentKey(String parentKey) {
        this.parentKey = parentKey;
    }

    public String getActiveName() {
        return activeName;
    }

    public void setActiveName(String activeName) {
        this.activeName = activeName;
    }

    public DriveEntryType getType() {
        return type;
    }

    public void setType(DriveEntryType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public DriveEntryStatus getStatus() {
        return status;
    }

    public void setStatus(DriveEntryStatus status) {
        this.status = status;
    }

    public Instant getTrashedAt() {
        return trashedAt;
    }

    public void setTrashedAt(Instant trashedAt) {
        this.trashedAt = trashedAt;
    }

    public Instant getDeleteAfter() {
        return deleteAfter;
    }

    public void setDeleteAfter(Instant deleteAfter) {
        this.deleteAfter = deleteAfter;
    }

    public UUID getTrashRootId() {
        return trashRootId;
    }

    public void setTrashRootId(UUID trashRootId) {
        this.trashRootId = trashRootId;
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
