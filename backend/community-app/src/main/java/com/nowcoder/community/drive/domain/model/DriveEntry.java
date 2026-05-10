package com.nowcoder.community.drive.domain.model;

import com.nowcoder.community.drive.domain.service.DriveEntryDomainService;

import java.time.Instant;
import java.util.UUID;

public record DriveEntry(
        UUID entryId,
        UUID spaceId,
        UUID parentId,
        DriveEntryType type,
        DriveEntryStatus status,
        String name,
        UUID objectId,
        UUID versionId,
        long sizeBytes,
        String mimeType,
        Instant trashedAt,
        Instant deleteAfter,
        UUID trashRootId,
        Instant createdAt,
        Instant updatedAt
) {
    private static final DriveEntryDomainService DOMAIN_SERVICE = new DriveEntryDomainService();

    public static DriveEntry folder(UUID entryId, UUID spaceId, UUID parentId, String name, Instant now) {
        requireId(entryId, "entryId");
        requireId(spaceId, "spaceId");
        requireNow(now);
        return new DriveEntry(
                entryId,
                spaceId,
                parentId,
                DriveEntryType.FOLDER,
                DriveEntryStatus.ACTIVE,
                normalize(name),
                null,
                null,
                0L,
                null,
                null,
                null,
                null,
                now,
                now
        );
    }

    public static DriveEntry file(UUID entryId, UUID spaceId, UUID parentId, String name, UUID objectId, UUID versionId, long sizeBytes, String mimeType, Instant now) {
        requireId(entryId, "entryId");
        requireId(spaceId, "spaceId");
        requireId(objectId, "objectId");
        requireId(versionId, "versionId");
        requireNow(now);
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
        return new DriveEntry(
                entryId,
                spaceId,
                parentId,
                DriveEntryType.FILE,
                DriveEntryStatus.ACTIVE,
                normalize(name),
                objectId,
                versionId,
                sizeBytes,
                mimeType,
                null,
                null,
                null,
                now,
                now
        );
    }

    public boolean folder() {
        return type == DriveEntryType.FOLDER;
    }

    public boolean file() {
        return type == DriveEntryType.FILE;
    }

    public DriveEntry rename(String newName, Instant now) {
        ensureMutable();
        requireNow(now);
        return new DriveEntry(
                entryId,
                spaceId,
                parentId,
                type,
                status,
                normalize(newName),
                objectId,
                versionId,
                sizeBytes,
                mimeType,
                trashedAt,
                deleteAfter,
                trashRootId,
                createdAt,
                now
        );
    }

    public DriveEntry moveTo(UUID newParentId, Instant now) {
        ensureMutable();
        requireNow(now);
        if (entryId != null && entryId.equals(newParentId)) {
            throw new IllegalArgumentException("folder cannot be moved into itself or descendant");
        }
        return new DriveEntry(
                entryId,
                spaceId,
                newParentId,
                type,
                status,
                name,
                objectId,
                versionId,
                sizeBytes,
                mimeType,
                trashedAt,
                deleteAfter,
                trashRootId,
                createdAt,
                now
        );
    }

    public DriveEntry trash(Instant trashedAt, Instant deleteAfter) {
        return trash(entryId, trashedAt, deleteAfter);
    }

    public DriveEntry trash(UUID trashRootId, Instant trashedAt, Instant deleteAfter) {
        requireId(trashRootId, "trashRootId");
        requireNow(trashedAt);
        if (deleteAfter == null) {
            throw new IllegalArgumentException("deleteAfter must not be null");
        }
        if (status == DriveEntryStatus.DELETED) {
            throw new IllegalStateException("trashed entry cannot be changed");
        }
        return new DriveEntry(
                entryId,
                spaceId,
                parentId,
                type,
                DriveEntryStatus.TRASHED,
                name,
                objectId,
                versionId,
                sizeBytes,
                mimeType,
                trashedAt,
                deleteAfter,
                trashRootId,
                createdAt,
                trashedAt
        );
    }

    public DriveEntry restore(UUID targetParentId, Instant now) {
        if (status != DriveEntryStatus.TRASHED) {
            throw new IllegalStateException("trashed entry cannot be changed");
        }
        requireNow(now);
        return new DriveEntry(
                entryId,
                spaceId,
                targetParentId,
                type,
                DriveEntryStatus.ACTIVE,
                name,
                objectId,
                versionId,
                sizeBytes,
                mimeType,
                null,
                null,
                null,
                createdAt,
                now
        );
    }

    public DriveEntry delete(Instant now) {
        requireNow(now);
        if (status == DriveEntryStatus.DELETED) {
            return this;
        }
        return new DriveEntry(
                entryId,
                spaceId,
                parentId,
                type,
                DriveEntryStatus.DELETED,
                name,
                objectId,
                versionId,
                sizeBytes,
                mimeType,
                trashedAt,
                deleteAfter,
                trashRootId,
                createdAt,
                now
        );
    }

    private static String normalize(String name) {
        return DOMAIN_SERVICE.normalizeName(name);
    }

    private void ensureMutable() {
        if (status != DriveEntryStatus.ACTIVE) {
            throw new IllegalStateException("trashed entry cannot be changed");
        }
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }

    private static void requireNow(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
    }
}
