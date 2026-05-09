package com.nowcoder.community.oss.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OssObjectAlias(
        String aliasKey,
        UUID objectId,
        UUID versionId,
        String status,
        Instant expiresAt,
        Instant createdAt
) {

    public OssObjectAlias {
        if (aliasKey == null || aliasKey.isBlank()) {
            throw new IllegalArgumentException("aliasKey must not be blank");
        }
        aliasKey = aliasKey.trim();
        Objects.requireNonNull(objectId, "objectId");
        Objects.requireNonNull(versionId, "versionId");
        status = status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase();
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static OssObjectAlias active(String aliasKey, UUID objectId, UUID versionId, Instant now) {
        return new OssObjectAlias(aliasKey, objectId, versionId, "ACTIVE", null, now);
    }

    public boolean activeAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return "ACTIVE".equals(status) && (expiresAt == null || expiresAt.isAfter(now));
    }
}
