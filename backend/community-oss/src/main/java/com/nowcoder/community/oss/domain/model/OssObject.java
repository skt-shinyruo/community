package com.nowcoder.community.oss.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OssObject(
        UUID objectId,
        String usage,
        String ownerService,
        String ownerDomain,
        String ownerType,
        String ownerId,
        OssVisibility visibility,
        OssObjectStatus status,
        UUID currentVersionId,
        String latestFileName,
        String latestContentType,
        long latestContentLength,
        String latestChecksumSha256,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {

    public OssObject {
        Objects.requireNonNull(objectId, "objectId");
        usage = requireText(usage, "usage");
        ownerService = requireText(ownerService, "ownerService");
        ownerDomain = requireText(ownerDomain, "ownerDomain");
        ownerType = requireText(ownerType, "ownerType");
        ownerId = requireText(ownerId, "ownerId");
        visibility = visibility == null ? OssVisibility.INTERNAL : visibility;
        status = status == null ? OssObjectStatus.STAGED : status;
        latestFileName = normalize(latestFileName);
        latestContentType = normalizeContentType(latestContentType);
        latestContentLength = Math.max(0, latestContentLength);
        latestChecksumSha256 = normalize(latestChecksumSha256);
        createdBy = normalize(createdBy);
        Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    public static OssObject stage(
            UUID objectId,
            String usage,
            String ownerService,
            String ownerDomain,
            String ownerType,
            String ownerId,
            OssVisibility visibility,
            String createdBy,
            Instant now
    ) {
        return new OssObject(
                objectId,
                usage,
                ownerService,
                ownerDomain,
                ownerType,
                ownerId,
                visibility,
                OssObjectStatus.STAGED,
                null,
                "",
                "application/octet-stream",
                0,
                "",
                createdBy,
                now,
                now
        );
    }

    public OssObject activate(OssObjectVersion version, Instant now) {
        Objects.requireNonNull(version, "version");
        if (!objectId.equals(version.objectId())) {
            throw new IllegalArgumentException("version belongs to a different object");
        }
        return new OssObject(
                objectId,
                usage,
                ownerService,
                ownerDomain,
                ownerType,
                ownerId,
                visibility,
                OssObjectStatus.ACTIVE,
                version.versionId(),
                version.fileName(),
                version.contentType(),
                version.contentLength(),
                version.checksumSha256(),
                createdBy,
                createdAt,
                now
        );
    }

    public OssObject deletePending(Instant now) {
        return new OssObject(
                objectId,
                usage,
                ownerService,
                ownerDomain,
                ownerType,
                ownerId,
                visibility,
                OssObjectStatus.DELETE_PENDING,
                currentVersionId,
                latestFileName,
                latestContentType,
                latestContentLength,
                latestChecksumSha256,
                createdBy,
                createdAt,
                now
        );
    }

    public OssObject purge(Instant now) {
        return new OssObject(
                objectId,
                usage,
                ownerService,
                ownerDomain,
                ownerType,
                ownerId,
                visibility,
                OssObjectStatus.PURGED,
                currentVersionId,
                latestFileName,
                latestContentType,
                latestContentLength,
                latestChecksumSha256,
                createdBy,
                createdAt,
                now
        );
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
