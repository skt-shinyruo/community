package com.nowcoder.community.common.pagination;

/**
 * Computes bounded mapper offsets without overflowing an {@code int}.
 */
public final class Pagination {

    private Pagination() {
    }

    public static int safeOffset(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);
        long offset = (long) safePage * safeSize;
        return offset >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) offset;
    }
}
