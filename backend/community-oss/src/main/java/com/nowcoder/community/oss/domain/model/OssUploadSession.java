package com.nowcoder.community.oss.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OssUploadSession(
        UUID sessionId,
        UUID requestId,
        UUID objectId,
        UUID versionId,
        String uploadMode,
        String ownerService,
        String ownerDomain,
        String ownerType,
        String ownerId,
        String expectedFileName,
        String expectedContentType,
        long expectedContentLength,
        String expectedChecksumSha256,
        OssUploadSessionStatus status,
        long claimVersion,
        Instant expiresAt,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        String lastError
) {

    public OssUploadSession {
        Objects.requireNonNull(sessionId, "sessionId");
        requestId = requestId == null ? sessionId : requestId;
        Objects.requireNonNull(objectId, "objectId");
        Objects.requireNonNull(versionId, "versionId");
        uploadMode = requireText(uploadMode, "uploadMode").toUpperCase();
        ownerService = requireText(ownerService, "ownerService");
        ownerDomain = requireText(ownerDomain, "ownerDomain");
        ownerType = requireText(ownerType, "ownerType");
        ownerId = requireText(ownerId, "ownerId");
        expectedFileName = requireText(expectedFileName, "expectedFileName");
        expectedContentType = normalizeContentType(expectedContentType);
        expectedContentLength = Math.max(0, expectedContentLength);
        expectedChecksumSha256 = normalize(expectedChecksumSha256);
        status = status == null ? OssUploadSessionStatus.READY : status;
        claimVersion = Math.max(0L, claimVersion);
        Objects.requireNonNull(expiresAt, "expiresAt");
        createdBy = normalize(createdBy);
        Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = updatedAt == null ? createdAt : updatedAt;
        lastError = normalize(lastError);
    }

    public OssUploadSession(
            UUID sessionId,
            UUID objectId,
            UUID versionId,
            String uploadMode,
            String ownerService,
            String ownerDomain,
            String ownerType,
            String ownerId,
            String expectedFileName,
            String expectedContentType,
            long expectedContentLength,
            String expectedChecksumSha256,
            OssUploadSessionStatus status,
            Instant expiresAt,
            String createdBy,
            Instant createdAt,
            Instant completedAt
    ) {
        this(
                sessionId,
                sessionId,
                objectId,
                versionId,
                uploadMode,
                ownerService,
                ownerDomain,
                ownerType,
                ownerId,
                expectedFileName,
                expectedContentType,
                expectedContentLength,
                expectedChecksumSha256,
                status,
                0L,
                expiresAt,
                createdBy,
                createdAt,
                completedAt == null ? createdAt : completedAt,
                completedAt,
                ""
        );
    }

    public static OssUploadSession ready(
            UUID sessionId,
            UUID objectId,
            UUID versionId,
            String uploadMode,
            String ownerService,
            String ownerDomain,
            String ownerType,
            String ownerId,
            String expectedFileName,
            String expectedContentType,
            long expectedContentLength,
            String expectedChecksumSha256,
            String createdBy,
            Instant now,
            Instant expiresAt
    ) {
        return ready(
                sessionId,
                sessionId,
                objectId,
                versionId,
                uploadMode,
                ownerService,
                ownerDomain,
                ownerType,
                ownerId,
                expectedFileName,
                expectedContentType,
                expectedContentLength,
                expectedChecksumSha256,
                createdBy,
                now,
                expiresAt
        );
    }

    public static OssUploadSession ready(
            UUID requestId,
            UUID sessionId,
            UUID objectId,
            UUID versionId,
            String uploadMode,
            String ownerService,
            String ownerDomain,
            String ownerType,
            String ownerId,
            String expectedFileName,
            String expectedContentType,
            long expectedContentLength,
            String expectedChecksumSha256,
            String createdBy,
            Instant now,
            Instant expiresAt
    ) {
        return new OssUploadSession(
                sessionId,
                requestId,
                objectId,
                versionId,
                uploadMode,
                ownerService,
                ownerDomain,
                ownerType,
                ownerId,
                expectedFileName,
                expectedContentType,
                expectedContentLength,
                expectedChecksumSha256,
                OssUploadSessionStatus.READY,
                0L,
                expiresAt,
                createdBy,
                now,
                now,
                null,
                ""
        );
    }

    public OssUploadSession startUploading(Instant now) {
        if (status != OssUploadSessionStatus.READY) {
            throw new IllegalStateException("upload session is not claimable: " + status);
        }
        return withStatus(OssUploadSessionStatus.UPLOADING, nextClaimVersion(), now, null, "");
    }

    public OssUploadSession renewReady(Instant now, Instant renewedExpiresAt) {
        if (status != OssUploadSessionStatus.READY) {
            throw new IllegalStateException("only a ready upload session can be renewed: " + status);
        }
        if (renewedExpiresAt == null || !renewedExpiresAt.isAfter(now)) {
            throw new IllegalArgumentException("renewed expiry must be after renewal time");
        }
        return withState(status, claimVersion, renewedExpiresAt, now, null, lastError);
    }

    public OssUploadSession recordPutFailure(Instant now, String failure) {
        String detail = normalize(failure);
        String evidence = detail.startsWith("PUT_FAILED:")
                ? detail
                : "PUT_FAILED:" + (detail.isEmpty() ? "unknown" : detail);
        return recordClaimError(now, evidence);
    }

    public OssUploadSession recordClaimError(Instant now, String error) {
        if (status != OssUploadSessionStatus.UPLOADING) {
            throw new IllegalStateException("upload session does not own an active claim: " + status);
        }
        return withStatus(status, claimVersion, now, completedAt, normalize(error));
    }

    public boolean hasPutFailureEvidence() {
        return status == OssUploadSessionStatus.UPLOADING && lastError.startsWith("PUT_FAILED:");
    }

    public OssUploadSession resetFailedClaim(Instant now) {
        Instant retryExpiresAt = expiresAt.isAfter(now) ? expiresAt : now.plusSeconds(1);
        return resetFailedClaim(now, retryExpiresAt);
    }

    public OssUploadSession resetFailedClaim(Instant now, Instant retryExpiresAt) {
        if (status != OssUploadSessionStatus.UPLOADING) {
            throw new IllegalStateException("upload session does not own a stale claim: " + status);
        }
        if (retryExpiresAt == null || !retryExpiresAt.isAfter(now)) {
            throw new IllegalArgumentException("retry expiry must be after reset time");
        }
        return withState(
                OssUploadSessionStatus.READY,
                nextClaimVersion(),
                retryExpiresAt,
                now,
                null,
                ""
        );
    }

    public OssUploadSession complete(Instant now) {
        if (status != OssUploadSessionStatus.READY && status != OssUploadSessionStatus.UPLOADING) {
            throw new IllegalStateException("upload session is not completable: " + status);
        }
        return withStatus(OssUploadSessionStatus.COMPLETED, claimVersion, now, now, "");
    }

    private OssUploadSession withStatus(
            OssUploadSessionStatus nextStatus,
            long nextClaimVersion,
            Instant nextUpdatedAt,
            Instant nextCompletedAt,
            String nextLastError
    ) {
        return withState(
                nextStatus,
                nextClaimVersion,
                expiresAt,
                nextUpdatedAt,
                nextCompletedAt,
                nextLastError
        );
    }

    private OssUploadSession withState(
            OssUploadSessionStatus nextStatus,
            long nextClaimVersion,
            Instant nextExpiresAt,
            Instant nextUpdatedAt,
            Instant nextCompletedAt,
            String nextLastError
    ) {
        return new OssUploadSession(
                sessionId,
                requestId,
                objectId,
                versionId,
                uploadMode,
                ownerService,
                ownerDomain,
                ownerType,
                ownerId,
                expectedFileName,
                expectedContentType,
                expectedContentLength,
                expectedChecksumSha256,
                nextStatus,
                nextClaimVersion,
                nextExpiresAt,
                createdBy,
                createdAt,
                nextUpdatedAt,
                nextCompletedAt,
                nextLastError
        );
    }

    public boolean expiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    private long nextClaimVersion() {
        return Math.addExact(claimVersion, 1L);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeContentType(String value) {
        return value == null || value.isBlank() ? "application/octet-stream" : value.trim();
    }
}
