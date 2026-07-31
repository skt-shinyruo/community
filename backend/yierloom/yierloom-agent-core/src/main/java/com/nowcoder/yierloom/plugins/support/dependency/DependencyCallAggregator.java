package com.nowcoder.yierloom.plugins.support.dependency;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class DependencyCallAggregator {
    private final int maxTrackedKeys;
    private final ConcurrentHashMap<DependencyCallKey, DependencyCallStats> stats =
            new ConcurrentHashMap<>();
    private final AtomicLong droppedKeys = new AtomicLong();

    public DependencyCallAggregator(int maxTrackedKeys) {
        if (maxTrackedKeys < 1) {
            throw new IllegalArgumentException("maxTrackedKeys must be positive");
        }
        this.maxTrackedKeys = maxTrackedKeys;
    }

    public boolean record(DependencyCallKey key, long durationMs, boolean error) {
        if (key == null) {
            droppedKeys.incrementAndGet();
            return false;
        }
        DependencyCallStats current = stats.get(key);
        if (current != null) {
            current.record(durationMs, error);
            return true;
        }
        return createOrRecord(key, durationMs, error);
    }

    public List<DependencyCallSnapshot> topSnapshots(int topN) {
        if (topN < 1) {
            throw new IllegalArgumentException("topN must be positive");
        }
        return stats.values().stream()
                .map(DependencyCallStats::snapshot)
                .sorted(Comparator.comparingLong(DependencyCallSnapshot::maxMs).reversed()
                        .thenComparing(snapshot -> snapshot.key().dimensions().toString()))
                .limit(topN)
                .toList();
    }

    public long droppedKeys() {
        return droppedKeys.get();
    }

    private boolean createOrRecord(DependencyCallKey key, long durationMs, boolean error) {
        synchronized (stats) {
            DependencyCallStats existing = stats.get(key);
            if (existing != null) {
                existing.record(durationMs, error);
                return true;
            }
            if (stats.size() >= maxTrackedKeys) {
                droppedKeys.incrementAndGet();
                return false;
            }
            DependencyCallStats created = new DependencyCallStats(key);
            created.record(durationMs, error);
            stats.put(key, created);
            return true;
        }
    }

    private static final class DependencyCallStats {
        private static final long[] UPPER_BOUNDS_MS = {
                1, 2, 5, 10, 20, 50, 100, 200, 500,
                1_000, 2_000, 5_000, 10_000, 30_000, 60_000, Long.MAX_VALUE
        };

        private final DependencyCallKey key;
        private final long[] buckets = new long[UPPER_BOUNDS_MS.length];
        private long count;
        private long totalMs;
        private long maxMs;
        private long errorCount;

        private DependencyCallStats(DependencyCallKey key) {
            this.key = key;
        }

        private synchronized void record(long durationMs, boolean error) {
            long safeDuration = Math.max(0, durationMs);
            count++;
            totalMs += safeDuration;
            maxMs = Math.max(maxMs, safeDuration);
            if (error) {
                errorCount++;
            }
            for (int index = 0; index < UPPER_BOUNDS_MS.length; index++) {
                if (safeDuration <= UPPER_BOUNDS_MS[index]) {
                    buckets[index]++;
                    break;
                }
            }
        }

        private synchronized DependencyCallSnapshot snapshot() {
            return new DependencyCallSnapshot(
                    key,
                    count,
                    count == 0 ? 0 : totalMs / count,
                    maxMs,
                    percentile95(),
                    errorCount);
        }

        private long percentile95() {
            if (count == 0) {
                return 0;
            }
            long target = Math.max(1, (long) Math.ceil(count * 0.95));
            long seen = 0;
            for (int index = 0; index < UPPER_BOUNDS_MS.length; index++) {
                seen += buckets[index];
                if (seen >= target) {
                    return UPPER_BOUNDS_MS[index] == Long.MAX_VALUE
                            ? 60_000
                            : UPPER_BOUNDS_MS[index];
                }
            }
            return 0;
        }
    }
}
