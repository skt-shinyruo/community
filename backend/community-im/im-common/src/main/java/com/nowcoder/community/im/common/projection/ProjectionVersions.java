package com.nowcoder.community.im.common.projection;

import java.util.concurrent.atomic.AtomicLong;

public final class ProjectionVersions {

    private static final int LOGICAL_BITS = 12;
    private static final long MAX_EPOCH_MILLIS = Long.MAX_VALUE >> LOGICAL_BITS;

    private ProjectionVersions() {
    }

    public static long fromEpochMillis(long epochMillis) {
        if (epochMillis <= 0) {
            return 0L;
        }
        return Math.min(epochMillis, MAX_EPOCH_MILLIS) << LOGICAL_BITS;
    }

    public static long snapshotHighWatermarkFromEpochMillis(long epochMillis) {
        long base = fromEpochMillis(epochMillis);
        return base <= 0L ? 0L : base - 1L;
    }

    public static long nextEventVersion(AtomicLong lastVersion, long occurredAtEpochMillis) {
        if (lastVersion == null) {
            return fromEpochMillis(occurredAtEpochMillis);
        }
        long base = fromEpochMillis(occurredAtEpochMillis);
        return lastVersion.updateAndGet(previous -> Math.max(previous + 1L, base));
    }

    public static long resolve(Long version, Long occurredAtEpochMillis, Long fallbackVersion) {
        long resolved = 0L;
        if (version != null && version > 0L) {
            resolved = version;
        } else if (occurredAtEpochMillis != null && occurredAtEpochMillis > 0L) {
            resolved = fromEpochMillis(occurredAtEpochMillis);
        }
        if (fallbackVersion != null && fallbackVersion > 0L) {
            resolved = Math.max(resolved, fallbackVersion);
        }
        return resolved;
    }

    public static Long max(Long left, Long right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.max(left, right);
    }

    public static long minPositiveOrZero(Long left, Long right) {
        long leftValue = left == null ? 0L : left;
        long rightValue = right == null ? 0L : right;
        if (leftValue <= 0L) {
            return Math.max(0L, rightValue);
        }
        if (rightValue <= 0L) {
            return leftValue;
        }
        return Math.min(leftValue, rightValue);
    }
}
