package com.nowcoder.observability.methodprofiler.stats;

import com.nowcoder.observability.methodprofiler.model.MethodKey;
import com.nowcoder.observability.methodprofiler.model.MethodSnapshot;

import java.util.concurrent.atomic.AtomicLong;

class MethodLatencyStats {

    private final MethodKey key;
    private final AtomicLong count = new AtomicLong();
    private final AtomicLong totalMs = new AtomicLong();
    private final AtomicLong maxMs = new AtomicLong();
    private final LatencyHistogram histogram = new LatencyHistogram();

    MethodLatencyStats(MethodKey key) {
        this.key = key;
    }

    void record(long durationMs) {
        long safeDuration = Math.max(0, durationMs);
        count.incrementAndGet();
        totalMs.addAndGet(safeDuration);
        histogram.record(safeDuration);
        maxMs.accumulateAndGet(safeDuration, Math::max);
    }

    MethodSnapshot snapshot() {
        long c = count.get();
        long total = totalMs.get();
        return new MethodSnapshot(key, c, c == 0 ? 0 : total / c, maxMs.get(), histogram.percentile95(c));
    }
}
