package com.nowcoder.observability.methodprofiler.config;

import java.time.Duration;
import java.util.List;

public record ProfilerConfig(
        boolean enabled,
        List<String> includes,
        List<String> excludes,
        long slowThresholdMs,
        Duration summaryInterval,
        int topN,
        double sampleRate,
        int maxEventsPerSecond,
        int maxTrackedMethods
) {

    public ProfilerConfig {
        includes = List.copyOf(includes == null || includes.isEmpty() ? List.of("*") : includes);
        excludes = List.copyOf(excludes == null ? List.of() : excludes);
        slowThresholdMs = Math.max(0, slowThresholdMs);
        summaryInterval = summaryInterval == null || summaryInterval.isNegative() || summaryInterval.isZero()
                ? Duration.ofSeconds(60)
                : summaryInterval;
        topN = Math.max(1, topN);
        sampleRate = Math.max(0.0, Math.min(1.0, sampleRate));
        maxEventsPerSecond = Math.max(0, maxEventsPerSecond);
        maxTrackedMethods = Math.max(1, maxTrackedMethods);
    }
}
