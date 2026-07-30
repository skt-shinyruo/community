package com.nowcoder.yierloom.plugins.method;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class MethodLatencyAggregator {
    private final int maxTrackedKeys;
    private final ConcurrentHashMap<MethodKey, MethodLatencyStats> stats = new ConcurrentHashMap<>();
    private final AtomicLong droppedKeys = new AtomicLong();

    public MethodLatencyAggregator(int maxTrackedKeys) {
        if (maxTrackedKeys < 1) {
            throw new IllegalArgumentException("maxTrackedKeys must be positive");
        }
        this.maxTrackedKeys = maxTrackedKeys;
    }

    public boolean record(MethodKey key, long durationMs) {
        if (key == null) {
            droppedKeys.incrementAndGet();
            return false;
        }
        MethodLatencyStats current = stats.get(key);
        if (current != null) {
            current.record(durationMs);
            return true;
        }
        return createOrRecord(key, durationMs);
    }

    public List<MethodSnapshot> topSnapshots(int topN) {
        if (topN < 1) {
            throw new IllegalArgumentException("topN must be positive");
        }
        return stats.values().stream()
                .map(MethodLatencyStats::snapshot)
                .sorted(Comparator.comparingLong(MethodSnapshot::maxMs).reversed()
                        .thenComparing(snapshot -> snapshot.key().signatureHash()))
                .limit(topN)
                .toList();
    }

    public long droppedKeys() {
        return droppedKeys.get();
    }

    private boolean createOrRecord(MethodKey key, long durationMs) {
        synchronized (stats) {
            MethodLatencyStats existing = stats.get(key);
            if (existing != null) {
                existing.record(durationMs);
                return true;
            }
            if (stats.size() >= maxTrackedKeys) {
                droppedKeys.incrementAndGet();
                return false;
            }
            MethodLatencyStats created = new MethodLatencyStats(key);
            created.record(durationMs);
            stats.put(key, created);
            return true;
        }
    }
}
