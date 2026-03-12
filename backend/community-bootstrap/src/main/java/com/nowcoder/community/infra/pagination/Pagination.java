package com.nowcoder.community.infra.pagination;

/**
 * Pagination helpers shared across modules.
 *
 * <p>Goal: avoid int overflow when computing {@code offset = page * size} (e.g. MySQL LIMIT offset),
 * which can overflow to a negative number when {@code page} is attacker-controlled.</p>
 */
public final class Pagination {

    private Pagination() {
    }

    /**
     * Computes a non-negative offset and prevents int overflow.
     *
     * <p>Implementation notes:
     * <ul>
     *   <li>{@code page} is clamped to {@code >= 0}</li>
     *   <li>{@code size} is clamped to {@code >= 1}</li>
     *   <li>Multiplication is performed in {@code long}</li>
     *   <li>The result is saturated to {@link Integer#MAX_VALUE} to fit mapper/JDBC int parameters</li>
     * </ul>
     */
    public static int safeOffset(int page, int size) {
        int p = Math.max(0, page);
        int s = Math.max(1, size);
        long offset = (long) p * (long) s;
        if (offset >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) offset;
    }
}

