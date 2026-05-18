package com.nowcoder.community.drive.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DriveSpace(
        UUID spaceId,
        UUID userId,
        long quotaBytes,
        long usedBytes,
        long reservedBytes,
        Instant createdAt,
        Instant updatedAt
) {
    public static final long DEFAULT_QUOTA_BYTES = 10L * 1024L * 1024L * 1024L;

    public static DriveSpace createDefault(UUID spaceId, UUID userId, Instant now) {
        requireId(spaceId, "spaceId");
        requireId(userId, "userId");
        requireNow(now);
        return new DriveSpace(spaceId, userId, DEFAULT_QUOTA_BYTES, 0L, 0L, now, now);
    }

    public long remainingBytes() {
        return Math.max(0L, quotaBytes - usedBytes - reservedBytes);
    }

    public DriveSpace reserve(long bytes, Instant now) {
        requireNonNegative(bytes, "bytes");
        requireNow(now);
        long nextReserved = reservedBytes + bytes;
        if (usedBytes + nextReserved > quotaBytes) {
            throw new IllegalStateException("drive quota exceeded");
        }
        return new DriveSpace(spaceId, userId, quotaBytes, usedBytes, nextReserved, createdAt, now);
    }

    public DriveSpace commitReserved(long bytes, Instant now) {
        requireNonNegative(bytes, "bytes");
        requireNow(now);
        if (bytes > reservedBytes) {
            throw new IllegalStateException("drive quota reservation insufficient");
        }
        long nextUsed = usedBytes + bytes;
        long nextReserved = reservedBytes - bytes;
        if (nextUsed + nextReserved > quotaBytes) {
            throw new IllegalStateException("drive quota exceeded");
        }
        return new DriveSpace(spaceId, userId, quotaBytes, nextUsed, nextReserved, createdAt, now);
    }

    public DriveSpace releaseReserved(long bytes, Instant now) {
        requireNonNegative(bytes, "bytes");
        requireNow(now);
        long nextReserved = Math.max(0L, reservedBytes - bytes);
        return new DriveSpace(spaceId, userId, quotaBytes, usedBytes, nextReserved, createdAt, now);
    }

    public DriveSpace release(long bytes, Instant now) {
        requireNonNegative(bytes, "bytes");
        requireNow(now);
        long nextUsed = Math.max(0L, usedBytes - bytes);
        return new DriveSpace(spaceId, userId, quotaBytes, nextUsed, reservedBytes, createdAt, now);
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

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
