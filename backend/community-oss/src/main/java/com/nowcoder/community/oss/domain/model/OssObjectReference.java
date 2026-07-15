package com.nowcoder.community.oss.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OssObjectReference(
        UUID referenceId,
        UUID objectId,
        UUID versionId,
        String subjectService,
        String subjectDomain,
        String subjectType,
        String subjectId,
        String referenceRole,
        OssObjectReferenceStatus status,
        Instant retainUntil,
        Instant createdAt,
        Instant releasedAt
) {

    public OssObjectReference {
        Objects.requireNonNull(referenceId, "referenceId");
        Objects.requireNonNull(objectId, "objectId");
        subjectService = requireText(subjectService, "subjectService");
        subjectDomain = requireText(subjectDomain, "subjectDomain");
        subjectType = requireText(subjectType, "subjectType");
        subjectId = requireText(subjectId, "subjectId");
        referenceRole = requireText(referenceRole, "referenceRole");
        status = status == null ? OssObjectReferenceStatus.ACTIVE : status;
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static OssObjectReference active(
            UUID referenceId,
            UUID objectId,
            UUID versionId,
            String subjectService,
            String subjectDomain,
            String subjectType,
            String subjectId,
            String referenceRole,
            Instant now,
            Instant retainUntil
    ) {
        return new OssObjectReference(
                referenceId,
                objectId,
                versionId,
                subjectService,
                subjectDomain,
                subjectType,
                subjectId,
                referenceRole,
                OssObjectReferenceStatus.ACTIVE,
                retainUntil,
                now,
                null
        );
    }

    public OssObjectReference release(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status == OssObjectReferenceStatus.RELEASED) {
            return this;
        }
        return new OssObjectReference(
                referenceId,
                objectId,
                versionId,
                subjectService,
                subjectDomain,
                subjectType,
                subjectId,
                referenceRole,
                OssObjectReferenceStatus.RELEASED,
                retainUntil,
                createdAt,
                now
        );
    }

    public boolean activeAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return status == OssObjectReferenceStatus.ACTIVE && (retainUntil == null || retainUntil.isAfter(now));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
