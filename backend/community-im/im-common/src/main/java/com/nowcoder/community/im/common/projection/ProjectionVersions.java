package com.nowcoder.community.im.common.projection;

public final class ProjectionVersions {

    private ProjectionVersions() {
    }

    public static long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0L) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    public static long requireNonNegative(Long value, String fieldName) {
        if (value == null || value < 0L) {
            throw new IllegalArgumentException(fieldName + " must be non-negative");
        }
        return value;
    }

    public static long snapshotEntryVersion(Long entryVersion, long watermark) {
        return Math.max(requirePositive(entryVersion, "version"), watermark);
    }
}
