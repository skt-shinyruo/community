package com.nowcoder.observability.methodprofiler.stats;

import com.nowcoder.observability.methodprofiler.model.MethodKey;
import com.nowcoder.observability.methodprofiler.model.MethodSnapshot;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MethodLatencyAggregator {

    private final int maxTrackedMethods;
    private final ConcurrentHashMap<MethodKey, MethodLatencyStats> stats = new ConcurrentHashMap<>();
    private final AtomicLong droppedMethodKeys = new AtomicLong();

    public MethodLatencyAggregator(int maxTrackedMethods) {
        this.maxTrackedMethods = Math.max(1, maxTrackedMethods);
    }

    public void record(MethodKey key, long durationMs) {
        if (key == null) {
            droppedMethodKeys.incrementAndGet();
            return;
        }
        MethodLatencyStats current = stats.get(key);
        if (current == null) {
            current = findOrCreateStats(key);
        }
        if (current != null) {
            current.record(durationMs);
        }
    }

    public List<MethodSnapshot> topSnapshots(int topN) {
        int limit = Math.max(1, topN);
        return stats.values().stream()
                .map(MethodLatencyStats::snapshot)
                .sorted(Comparator.comparingLong(MethodSnapshot::maxMs).reversed()
                        .thenComparing(snapshot -> snapshot.key().signatureHash()))
                .limit(limit)
                .toList();
    }

    public long droppedMethodKeys() {
        return droppedMethodKeys.get();
    }

    private MethodLatencyStats findOrCreateStats(MethodKey key) {
        synchronized (stats) {
            MethodLatencyStats existing = stats.get(key);
            if (existing != null) {
                return existing;
            }
            if (stats.size() >= maxTrackedMethods) {
                droppedMethodKeys.incrementAndGet();
                return null;
            }
            MethodLatencyStats created = new MethodLatencyStats(key);
            stats.put(key, created);
            return created;
        }
    }
}
