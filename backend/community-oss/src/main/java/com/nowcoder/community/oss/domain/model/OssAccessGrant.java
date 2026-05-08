package com.nowcoder.community.oss.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OssAccessGrant(
        UUID grantId,
        UUID objectId,
        UUID versionId,
        String principalType,
        String principalValue,
        String permission,
        Instant expiresAt,
        String createdBy,
        Instant createdAt,
        Instant revokedAt
) {

    public OssAccessGrant {
        Objects.requireNonNull(grantId, "grantId");
        Objects.requireNonNull(objectId, "objectId");
        principalType = requireText(principalType, "principalType").toUpperCase();
        principalValue = requireText(principalValue, "principalValue");
        permission = requireText(permission, "permission").toUpperCase();
        createdBy = normalize(createdBy);
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static OssAccessGrant readGrant(
            UUID grantId,
            UUID objectId,
            UUID versionId,
            String principalType,
            String principalValue,
            String createdBy,
            Instant now,
            Instant expiresAt
    ) {
        return new OssAccessGrant(
                grantId,
                objectId,
                versionId,
                principalType,
                principalValue,
                "READ",
                expiresAt,
                createdBy,
                now,
                null
        );
    }

    public boolean activeAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }

    public OssAccessGrant revoke(Instant now) {
        return new OssAccessGrant(
                grantId,
                objectId,
                versionId,
                principalType,
                principalValue,
                permission,
                expiresAt,
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
}
