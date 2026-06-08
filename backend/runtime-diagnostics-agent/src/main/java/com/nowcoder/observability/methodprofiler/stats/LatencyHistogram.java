package com.nowcoder.observability.methodprofiler.stats;

import java.util.concurrent.atomic.AtomicLongArray;

class LatencyHistogram {

    private static final long[] UPPER_BOUNDS_MS = {
            1, 2, 5, 10, 20, 50, 100, 200, 500,
            1_000, 2_000, 5_000, 10_000, 30_000, 60_000, Long.MAX_VALUE
    };

    private final AtomicLongArray buckets = new AtomicLongArray(UPPER_BOUNDS_MS.length);

    void record(long durationMs) {
        long safeDuration = Math.max(0, durationMs);
        for (int i = 0; i < UPPER_BOUNDS_MS.length; i++) {
            if (safeDuration <= UPPER_BOUNDS_MS[i]) {
                buckets.incrementAndGet(i);
                return;
            }
        }
    }

    long percentile95(long totalCount) {
        if (totalCount <= 0) {
            return 0;
        }
        long target = Math.max(1, (long) Math.ceil(totalCount * 0.95));
        long seen = 0;
        for (int i = 0; i < UPPER_BOUNDS_MS.length; i++) {
            seen += buckets.get(i);
            if (seen >= target) {
                return UPPER_BOUNDS_MS[i] == Long.MAX_VALUE ? 60_000 : UPPER_BOUNDS_MS[i];
            }
        }
        return 0;
    }
}
