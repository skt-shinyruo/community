package com.nowcoder.community.drive.domain.model;

import java.time.Instant;
import java.util.UUID;

public record DriveShare(
        UUID shareId,
        UUID entryId,
        String shareToken,
        String passwordHash,
        Instant expiresAt,
        UUID createdBy,
        DriveShareStatus status,
        Instant createdAt,
        Instant revokedAt
) {
    public static DriveShare active(UUID shareId, UUID entryId, String shareToken, String passwordHash, Instant expiresAt, UUID createdBy, Instant now) {
        requireId(shareId, "shareId");
        requireId(entryId, "entryId");
        requireId(createdBy, "createdBy");
        requireNow(expiresAt);
        requireNow(now);
        if (shareToken == null || shareToken.isBlank()) {
            throw new IllegalArgumentException("shareToken must not be blank");
        }
        return new DriveShare(
                shareId,
                entryId,
                shareToken,
                passwordHash,
                expiresAt,
                createdBy,
                DriveShareStatus.ACTIVE,
                now,
                null
        );
    }

    public boolean activeAt(Instant now) {
        requireNow(now);
        return status == DriveShareStatus.ACTIVE && now.isBefore(expiresAt);
    }

    public DriveShare revoke(Instant now) {
        requireNow(now);
        return new DriveShare(
                shareId,
                entryId,
                shareToken,
                passwordHash,
                expiresAt,
                createdBy,
                DriveShareStatus.REVOKED,
                createdAt,
                now
        );
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
