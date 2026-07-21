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
        String checksumSha256,
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

    public static DriveUpload prepared(UUID uploadId, UUID spaceId, UUID parentId, String name,
                                       long sizeBytes, String mimeType, String checksumSha256,
                                       UUID objectId, UUID versionId, UUID ossSessionId,
                                       UUID createdBy, Instant now, Instant expiresAt) {
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
                normalizeChecksum(checksumSha256),
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

    public DriveUpload startCompleting(UUID entryId, Instant now) {
        requireId(entryId, "entryId");
        requireNow(now);
        if (status == DriveUploadStatus.COMPLETED
                || status == DriveUploadStatus.COMPLETING
                || status == DriveUploadStatus.OBJECT_COMPLETED) {
            return this;
        }
        if (expiredAt(now)) {
            return expire(now);
        }
        if (status != DriveUploadStatus.PREPARED) {
            throw new IllegalStateException("upload is not ready to complete: " + status);
        }
        return withState(DriveUploadStatus.COMPLETING, entryId, now, null);
    }

    public DriveUpload markObjectCompleted(Instant now) {
        requireNow(now);
        if (status == DriveUploadStatus.OBJECT_COMPLETED || status == DriveUploadStatus.COMPLETED) {
            return this;
        }
        if (status != DriveUploadStatus.COMPLETING) {
            throw new IllegalStateException("upload object completion cannot be recorded from: " + status);
        }
        requireId(completedEntryId, "completedEntryId");
        return withState(DriveUploadStatus.OBJECT_COMPLETED, completedEntryId, now, null);
    }

    public DriveUpload completeFinalization(Instant now) {
        requireNow(now);
        if (status == DriveUploadStatus.COMPLETED) {
            return this;
        }
        if (status != DriveUploadStatus.OBJECT_COMPLETED) {
            throw new IllegalStateException("upload finalization cannot be completed from: " + status);
        }
        requireId(completedEntryId, "completedEntryId");
        return withState(DriveUploadStatus.COMPLETED, completedEntryId, now, now);
    }

    public DriveUpload failCompletion(Instant now) {
        requireNow(now);
        if (status == DriveUploadStatus.COMPLETED) {
            return this;
        }
        return withState(DriveUploadStatus.FAILED, completedEntryId, now, completedAt);
    }

    public DriveUpload complete(UUID entryId, Instant now) {
        requireId(entryId, "entryId");
        requireNow(now);
        if (expiredAt(now)) {
            return expire(now);
        }
        return withState(DriveUploadStatus.COMPLETED, entryId, now, now);
    }

    private DriveUpload expire(Instant now) {
        return withState(DriveUploadStatus.EXPIRED, completedEntryId, now, completedAt);
    }

    private DriveUpload withState(DriveUploadStatus nextStatus, UUID nextCompletedEntryId, Instant nextUpdatedAt, Instant nextCompletedAt) {
        return new DriveUpload(
                uploadId,
                spaceId,
                parentId,
                name,
                sizeBytes,
                mimeType,
                checksumSha256,
                objectId,
                versionId,
                ossSessionId,
                createdBy,
                nextStatus,
                nextCompletedEntryId,
                createdAt,
                nextUpdatedAt,
                expiresAt,
                nextCompletedAt
        );
    }

    private static String normalize(String name) {
        return DOMAIN_SERVICE.normalizeName(name);
    }

    private static String normalizeChecksum(String checksumSha256) {
        return checksumSha256 == null ? "" : checksumSha256.trim();
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
