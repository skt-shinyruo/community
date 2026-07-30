package com.nowcoder.yierloom.plugins.method;

final class MethodLatencyStats {
    private final MethodKey key;
    private final LatencyHistogram histogram = new LatencyHistogram();
    private long count;
    private long totalMs;
    private long maxMs;

    MethodLatencyStats(MethodKey key) {
        this.key = key;
    }

    synchronized void record(long durationMs) {
        long safeDuration = Math.max(0, durationMs);
        count++;
        totalMs += safeDuration;
        maxMs = Math.max(maxMs, safeDuration);
        histogram.record(safeDuration);
    }

    synchronized MethodSnapshot snapshot() {
        return new MethodSnapshot(
                key,
                count,
                count == 0 ? 0 : totalMs / count,
                maxMs,
                histogram.percentile95(count));
    }
}
