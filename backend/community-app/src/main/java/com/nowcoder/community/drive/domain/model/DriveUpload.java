package com.nowcoder.community.drive.domain.model;

import com.nowcoder.community.drive.domain.service.DriveEntryDomainService;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
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

    private static final Duration COMPLETION_RECOVERY_WINDOW = Duration.ofHours(1);

    public DriveUpload {
        requireId(uploadId, "uploadId");
        requireId(spaceId, "spaceId");
        requireId(createdBy, "createdBy");
        Objects.requireNonNull(status, "status must not be null");
        requireNow(createdAt);
        requireNow(updatedAt);
        requireNow(expiresAt);
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
        boolean hasAnyOssIdentity = objectId != null || versionId != null || ossSessionId != null;
        boolean hasCompleteOssIdentity = objectId != null && versionId != null && ossSessionId != null && expiresAt != null;
        if (status == DriveUploadStatus.PREPARING) {
            if (objectId != null || versionId != null || ossSessionId != null) {
                throw new IllegalArgumentException("preparing upload must not have OSS identifiers");
            }
        } else if (status == DriveUploadStatus.PREPARED
                || status == DriveUploadStatus.COMPLETING
                || status == DriveUploadStatus.OBJECT_COMPLETED
                || status == DriveUploadStatus.CLEANUP_PENDING
                || status == DriveUploadStatus.COMPLETED) {
            if (!hasCompleteOssIdentity) {
                throw new IllegalArgumentException("prepared upload must have complete OSS identifiers");
            }
        } else if (hasAnyOssIdentity && !hasCompleteOssIdentity) {
            throw new IllegalArgumentException("terminal upload has incomplete OSS identifiers");
        }
    }

    public static DriveUpload preparing(UUID uploadId, UUID spaceId, UUID parentId, String name,
                                        long sizeBytes, String mimeType, String checksumSha256,
                                        UUID createdBy, Instant now, Instant expiresAt) {
        return new DriveUpload(
                uploadId,
                spaceId,
                parentId,
                normalize(name),
                sizeBytes,
                mimeType,
                normalizeChecksum(checksumSha256),
                null,
                null,
                null,
                createdBy,
                DriveUploadStatus.PREPARING,
                null,
                now,
                now,
                expiresAt,
                null
        );
    }

    public static DriveUpload prepared(UUID uploadId, UUID spaceId, UUID parentId, String name,
                                       long sizeBytes, String mimeType, String checksumSha256,
                                       UUID objectId, UUID versionId, UUID ossSessionId,
                                       UUID createdBy, Instant now, Instant expiresAt) {
        return preparing(uploadId, spaceId, parentId, name, sizeBytes, mimeType, checksumSha256,
                createdBy, now, expiresAt)
                .markPrepared(objectId, versionId, ossSessionId, expiresAt, now);
    }

    public DriveUpload markPrepared(UUID objectId, UUID versionId, UUID ossSessionId,
                                    Instant expiresAt, Instant now) {
        requireNow(now);
        if (status == DriveUploadStatus.PREPARED) {
            if (Objects.equals(this.objectId, objectId)
                    && Objects.equals(this.versionId, versionId)
                    && Objects.equals(this.ossSessionId, ossSessionId)
                    && Objects.equals(this.expiresAt, expiresAt)) {
                return this;
            }
            throw new IllegalStateException("upload was prepared with different OSS identifiers");
        }
        if (status != DriveUploadStatus.PREPARING) {
            throw new IllegalStateException("upload preparation cannot be completed from: " + status);
        }
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
                DriveUploadStatus.PREPARED,
                null,
                createdAt,
                now,
                expiresAt,
                null
        );
    }

    public DriveUpload failPreparation(Instant now) {
        requireNow(now);
        if (status == DriveUploadStatus.FAILED) {
            return this;
        }
        if (status != DriveUploadStatus.PREPARING) {
            throw new IllegalStateException("upload preparation cannot fail from: " + status);
        }
        return preparingTerminalState(DriveUploadStatus.FAILED, now);
    }

    public DriveUpload expirePreparation(Instant now) {
        requireNow(now);
        if (status == DriveUploadStatus.EXPIRED) {
            return this;
        }
        if (status != DriveUploadStatus.PREPARING) {
            throw new IllegalStateException("upload preparation cannot expire from: " + status);
        }
        return preparingTerminalState(DriveUploadStatus.EXPIRED, now);
    }

    public boolean matchesPrepared(UUID objectId, UUID versionId, UUID ossSessionId, Instant expiresAt) {
        return status == DriveUploadStatus.PREPARED
                && Objects.equals(this.objectId, objectId)
                && Objects.equals(this.versionId, versionId)
                && Objects.equals(this.ossSessionId, ossSessionId)
                && Objects.equals(this.expiresAt, expiresAt);
    }

    public boolean expiredAt(Instant now) {
        requireNow(now);
        return switch (status) {
            case EXPIRED -> true;
            case PREPARING, PREPARED -> !now.isBefore(expiresAt);
            case COMPLETING, OBJECT_COMPLETED -> !now.isBefore(expiresAt);
            case CLEANUP_PENDING, COMPLETED, FAILED -> false;
        };
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
        if (status == DriveUploadStatus.PREPARING) {
            throw new IllegalStateException("upload is still preparing");
        }
        if (expiredAt(now)) {
            return expire(now);
        }
        if (status != DriveUploadStatus.PREPARED) {
            throw new IllegalStateException("upload is not ready to complete: " + status);
        }
        return withCompletionDeadline(DriveUploadStatus.COMPLETING, entryId, now);
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
        return withCompletionDeadline(DriveUploadStatus.OBJECT_COMPLETED, completedEntryId, now);
    }

    public DriveUpload complete(Instant now) {
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

    public DriveUpload expire(Instant now) {
        requireNow(now);
        if (status == DriveUploadStatus.EXPIRED) {
            return this;
        }
        if (status != DriveUploadStatus.PREPARED) {
            throw new IllegalStateException("upload cannot expire from: " + status);
        }
        if (!expiredAt(now)) {
            throw new IllegalStateException("upload has not expired");
        }
        return withState(DriveUploadStatus.EXPIRED, completedEntryId, now, completedAt);
    }

    public DriveUpload startCleanup(Instant now) {
        requireNow(now);
        if (status == DriveUploadStatus.CLEANUP_PENDING) {
            return this;
        }
        if (status != DriveUploadStatus.COMPLETING && status != DriveUploadStatus.OBJECT_COMPLETED) {
            throw new IllegalStateException("upload cleanup cannot start from: " + status);
        }
        return withState(DriveUploadStatus.CLEANUP_PENDING, completedEntryId, now, completedAt);
    }

    public DriveUpload completeCleanup(Instant now) {
        requireNow(now);
        if (status == DriveUploadStatus.FAILED) {
            return this;
        }
        if (status != DriveUploadStatus.CLEANUP_PENDING) {
            throw new IllegalStateException("upload cleanup cannot complete from: " + status);
        }
        return withState(DriveUploadStatus.FAILED, completedEntryId, now, completedAt);
    }

    private DriveUpload preparingTerminalState(DriveUploadStatus nextStatus, Instant now) {
        return new DriveUpload(
                uploadId,
                spaceId,
                parentId,
                name,
                sizeBytes,
                mimeType,
                checksumSha256,
                null,
                null,
                null,
                createdBy,
                nextStatus,
                null,
                createdAt,
                now,
                expiresAt,
                completedAt
        );
    }

    private DriveUpload withState(DriveUploadStatus nextStatus, UUID nextCompletedEntryId,
                                  Instant nextUpdatedAt, Instant nextCompletedAt) {
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

    private DriveUpload withCompletionDeadline(DriveUploadStatus nextStatus, UUID nextCompletedEntryId, Instant now) {
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
                now,
                now.plus(COMPLETION_RECOVERY_WINDOW),
                completedAt
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
