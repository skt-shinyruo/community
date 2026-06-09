package com.nowcoder.observability.runtimediagnostics.probes.dependency;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class DependencyCallAggregator {

    private final int maxTrackedKeys;
    private final ConcurrentHashMap<DependencyCallKey, DependencyCallStats> stats = new ConcurrentHashMap<>();
    private final AtomicLong droppedKeys = new AtomicLong();

    public DependencyCallAggregator(int maxTrackedKeys) {
        this.maxTrackedKeys = Math.max(1, maxTrackedKeys);
    }

    public void record(DependencyCallKey key, long durationMs, boolean error) {
        if (key == null) {
            droppedKeys.incrementAndGet();
            return;
        }
        DependencyCallStats current = stats.get(key);
        if (current == null) {
            current = findOrCreateStats(key);
        }
        if (current != null) {
            current.record(durationMs, error);
        }
    }

    public List<DependencyCallSnapshot> topSnapshots(String probe, int topN) {
        int limit = Math.max(1, topN);
        return stats.values().stream()
                .map(DependencyCallStats::snapshot)
                .filter(snapshot -> probe == null || probe.equals(snapshot.key().probe()))
                .sorted(Comparator.comparingLong(DependencyCallSnapshot::maxMs).reversed()
                        .thenComparing(snapshot -> snapshot.key().toString()))
                .limit(limit)
                .toList();
    }

    public long droppedKeys() {
        return droppedKeys.get();
    }

    private DependencyCallStats findOrCreateStats(DependencyCallKey key) {
        synchronized (stats) {
            DependencyCallStats existing = stats.get(key);
            if (existing != null) {
                return existing;
            }
            if (stats.size() >= maxTrackedKeys) {
                droppedKeys.incrementAndGet();
                return null;
            }
            DependencyCallStats created = new DependencyCallStats(key);
            stats.put(key, created);
            return created;
        }
    }

    private static final class DependencyCallStats {
        private static final long[] UPPER_BOUNDS_MS = {
                1, 2, 5, 10, 20, 50, 100, 200, 500,
                1_000, 2_000, 5_000, 10_000, 30_000, 60_000, Long.MAX_VALUE
        };

        private final DependencyCallKey key;
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong totalMs = new AtomicLong();
        private final AtomicLong maxMs = new AtomicLong();
        private final AtomicLong errorCount = new AtomicLong();
        private final AtomicLongArray buckets = new AtomicLongArray(UPPER_BOUNDS_MS.length);

        private DependencyCallStats(DependencyCallKey key) {
            this.key = key;
        }

        private void record(long durationMs, boolean error) {
            long safeDuration = Math.max(0, durationMs);
            count.incrementAndGet();
            totalMs.addAndGet(safeDuration);
            maxMs.accumulateAndGet(safeDuration, Math::max);
            if (error) {
                errorCount.incrementAndGet();
            }
            recordBucket(safeDuration);
        }

        private DependencyCallSnapshot snapshot() {
            long c = count.get();
            long total = totalMs.get();
            return new DependencyCallSnapshot(key, c, c == 0 ? 0 : total / c, maxMs.get(), percentile95(c),
                    errorCount.get());
        }

        private void recordBucket(long durationMs) {
            for (int i = 0; i < UPPER_BOUNDS_MS.length; i++) {
                if (durationMs <= UPPER_BOUNDS_MS[i]) {
                    buckets.incrementAndGet(i);
                    return;
                }
            }
        }

        private long percentile95(long totalCount) {
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
}
