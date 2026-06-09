package com.nowcoder.observability.runtimediagnostics.config;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

public record DiagnosticsConfig(
        boolean enabled,
        List<String> probes,
        List<String> includes,
        List<String> excludes,
        double sampleRate,
        int maxEventsPerSecond,
        Duration summaryInterval,
        int topN,
        int maxTrackedKeys,
        long methodSlowThresholdMs,
        Duration threadSnapshotInterval,
        Duration jvmSummaryInterval,
        long httpSlowThresholdMs,
        long jdbcSlowThresholdMs,
        long redisSlowThresholdMs,
        long kafkaSlowThresholdMs,
        double httpSampleRate,
        double jdbcSampleRate,
        double redisSampleRate,
        double kafkaSampleRate,
        int httpMaxEventsPerSecond,
        int jdbcMaxEventsPerSecond,
        int redisMaxEventsPerSecond,
        int kafkaMaxEventsPerSecond,
        boolean kafkaTopicNamesEnabled
) {

    private static final List<String> DEFAULT_PROBES = List.of("method", "exception", "thread", "jvm");
    private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(60);

    public DiagnosticsConfig {
        probes = normalizeProbes(probes);
        includes = normalizePatterns(includes, List.of("*"));
        excludes = normalizePatterns(excludes, List.of());
        sampleRate = clampSampleRate(sampleRate);
        maxEventsPerSecond = Math.max(0, maxEventsPerSecond);
        summaryInterval = positiveOrDefault(summaryInterval);
        topN = Math.max(1, topN);
        maxTrackedKeys = Math.max(1, maxTrackedKeys);
        methodSlowThresholdMs = Math.max(0, methodSlowThresholdMs);
        threadSnapshotInterval = positiveOrDefault(threadSnapshotInterval);
        jvmSummaryInterval = positiveOrDefault(jvmSummaryInterval);
        httpSlowThresholdMs = Math.max(0, httpSlowThresholdMs);
        jdbcSlowThresholdMs = Math.max(0, jdbcSlowThresholdMs);
        redisSlowThresholdMs = Math.max(0, redisSlowThresholdMs);
        kafkaSlowThresholdMs = Math.max(0, kafkaSlowThresholdMs);
        httpSampleRate = clampSampleRate(httpSampleRate);
        jdbcSampleRate = clampSampleRate(jdbcSampleRate);
        redisSampleRate = clampSampleRate(redisSampleRate);
        kafkaSampleRate = clampSampleRate(kafkaSampleRate);
        httpMaxEventsPerSecond = Math.max(0, httpMaxEventsPerSecond);
        jdbcMaxEventsPerSecond = Math.max(0, jdbcMaxEventsPerSecond);
        redisMaxEventsPerSecond = Math.max(0, redisMaxEventsPerSecond);
        kafkaMaxEventsPerSecond = Math.max(0, kafkaMaxEventsPerSecond);
    }

    public boolean probeEnabled(String probe) {
        return probe != null && probes.contains(probe.toLowerCase(Locale.ROOT));
    }

    private static List<String> normalizeProbes(List<String> values) {
        if (values == null || values.isEmpty()) {
            return DEFAULT_PROBES;
        }
        List<String> normalized = values.stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
        return normalized.isEmpty() ? DEFAULT_PROBES : List.copyOf(normalized);
    }

    private static List<String> normalizePatterns(List<String> values, List<String> fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        List<String> normalized = values.stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        return normalized.isEmpty() ? fallback : List.copyOf(normalized);
    }

    private static Duration positiveOrDefault(Duration value) {
        return value == null || value.isZero() || value.isNegative() ? DEFAULT_INTERVAL : value;
    }

    private static double clampSampleRate(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 1.0;
    }
}
