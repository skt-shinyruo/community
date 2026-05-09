package com.nowcoder.community.drive.domain.model;

import com.nowcoder.community.drive.domain.service.DriveEntryDomainService;

import java.time.Instant;
import java.util.UUID;

public record DriveUpload(
        UUID uploadId,
        UUID spaceId,
        UUID parentId,
        String name,
        long sizeBytes,
        String mimeType,
        UUID objectId,
        UUID versionId,
        UUID ossSessionId,
        UUID createdBy,
        DriveUploadStatus status,
        UUID completedEntryId,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        Instant completedAt
) {
    private static final DriveEntryDomainService DOMAIN_SERVICE = new DriveEntryDomainService();

    public static DriveUpload prepared(UUID uploadId, UUID spaceId, UUID parentId, String name, long sizeBytes, String mimeType, UUID objectId, UUID versionId, UUID ossSessionId, UUID createdBy, Instant now, Instant expiresAt) {
        requireId(uploadId, "uploadId");
        requireId(spaceId, "spaceId");
        requireId(objectId, "objectId");
        requireId(versionId, "versionId");
        requireId(ossSessionId, "ossSessionId");
        requireId(createdBy, "createdBy");
        requireNow(now);
        requireNow(expiresAt);
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
        return new DriveUpload(
                uploadId,
                spaceId,
                parentId,
                normalize(name),
                sizeBytes,
                mimeType,
                objectId,
                versionId,
                ossSessionId,
                createdBy,
                DriveUploadStatus.PREPARED,
                null,
                now,
                now,
                expiresAt,
                null
        );
    }

    public boolean expiredAt(Instant now) {
        requireNow(now);
        return status == DriveUploadStatus.EXPIRED || !now.isBefore(expiresAt);
    }

    public boolean completed() {
        return status == DriveUploadStatus.COMPLETED;
    }

    public DriveUpload complete(UUID entryId, Instant now) {
        requireId(entryId, "entryId");
        requireNow(now);
        if (expiredAt(now)) {
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
                    DriveUploadStatus.EXPIRED,
                    completedEntryId,
                    createdAt,
                    now,
                    expiresAt,
                    completedAt
            );
        }
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
                DriveUploadStatus.COMPLETED,
                entryId,
                createdAt,
                now,
                expiresAt,
                now
        );
    }

    private static String normalize(String name) {
        return DOMAIN_SERVICE.normalizeName(name);
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
