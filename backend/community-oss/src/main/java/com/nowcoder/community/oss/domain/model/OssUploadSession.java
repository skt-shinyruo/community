package com.nowcoder.community.oss.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OssUploadSession(
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
        String aliasKey,
        OssUploadSessionStatus status,
        Instant expiresAt,
        String createdBy,
        Instant createdAt,
        Instant completedAt
) {

    public OssUploadSession {
        Objects.requireNonNull(sessionId, "sessionId");
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
        aliasKey = normalize(aliasKey);
        status = status == null ? OssUploadSessionStatus.READY : status;
        Objects.requireNonNull(expiresAt, "expiresAt");
        createdBy = normalize(createdBy);
        Objects.requireNonNull(createdAt, "createdAt");
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
            String aliasKey,
            String createdBy,
            Instant now,
            Instant expiresAt
    ) {
        return new OssUploadSession(
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
                aliasKey,
                OssUploadSessionStatus.READY,
                expiresAt,
                createdBy,
                now,
                null
        );
    }

    public OssUploadSession complete(Instant now) {
        if (status != OssUploadSessionStatus.READY && status != OssUploadSessionStatus.UPLOADING) {
            throw new IllegalStateException("upload session is not completable: " + status);
        }
        return new OssUploadSession(
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
                aliasKey,
                OssUploadSessionStatus.COMPLETED,
                expiresAt,
                createdBy,
                createdAt,
                now
        );
    }

    public boolean expiredAt(Instant now) {
        return !expiresAt.isAfter(now);
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
