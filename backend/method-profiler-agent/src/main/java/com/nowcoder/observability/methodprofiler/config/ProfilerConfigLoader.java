package com.nowcoder.observability.methodprofiler.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class ProfilerConfigLoader {

    private ProfilerConfigLoader() {
    }

    public static ProfilerConfig load(String agentArgs) {
        return load(agentArgs, propertiesMap(System.getProperties()), System.getenv());
    }

    static ProfilerConfig load(String agentArgs, Map<String, String> systemProperties, Map<String, String> environment) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("enabled", first(systemProperties.get("method.profiler.enabled"), environment.get("METHOD_PROFILER_ENABLED"), "false"));
        values.put("includes", first(systemProperties.get("method.profiler.includes"), environment.get("METHOD_PROFILER_INCLUDES"), "*"));
        values.put("excludes", first(systemProperties.get("method.profiler.excludes"), environment.get("METHOD_PROFILER_EXCLUDES"), ""));
        values.put("slowThresholdMs", first(systemProperties.get("method.profiler.slowThresholdMs"), environment.get("METHOD_PROFILER_SLOW_THRESHOLD_MS"), "100"));
        values.put("summaryInterval", first(systemProperties.get("method.profiler.summaryInterval"), environment.get("METHOD_PROFILER_SUMMARY_INTERVAL"), "60s"));
        values.put("topN", first(systemProperties.get("method.profiler.topN"), environment.get("METHOD_PROFILER_TOP_N"), "50"));
        values.put("sampleRate", first(systemProperties.get("method.profiler.sampleRate"), environment.get("METHOD_PROFILER_SAMPLE_RATE"), "1.0"));
        values.put("maxEventsPerSecond", first(systemProperties.get("method.profiler.maxEventsPerSecond"), environment.get("METHOD_PROFILER_MAX_EVENTS_PER_SECOND"), "20"));
        values.put("maxTrackedMethods", first(systemProperties.get("method.profiler.maxTrackedMethods"), environment.get("METHOD_PROFILER_MAX_TRACKED_METHODS"), "10000"));
        parseAgentArgs(agentArgs).forEach(values::put);

        return new ProfilerConfig(
                Boolean.parseBoolean(values.get("enabled")),
                csv(values.get("includes")),
                csv(values.get("excludes")),
                parseLong(values.get("slowThresholdMs"), 100),
                parseDuration(values.get("summaryInterval"), Duration.ofSeconds(60)),
                parseInt(values.get("topN"), 50),
                parseDouble(values.get("sampleRate"), 1.0),
                parseInt(values.get("maxEventsPerSecond"), 20),
                parseInt(values.get("maxTrackedMethods"), 10_000)
        );
    }

    private static Map<String, String> parseAgentArgs(String agentArgs) {
        Map<String, String> result = new LinkedHashMap<>();
        if (agentArgs == null || agentArgs.isBlank()) {
            return result;
        }
        for (String pair : agentArgs.split(",")) {
            int index = pair.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String key = pair.substring(0, index).trim();
            String value = pair.substring(index + 1).trim();
            if (!key.isEmpty()) {
                result.put(key, value);
            }
        }
        return result;
    }

    private static List<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private static String first(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static Duration parseDuration(String value, Duration fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        try {
            if (normalized.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(normalized.substring(0, normalized.length() - 2)));
            }
            if (normalized.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
            }
            if (normalized.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
            }
            return Duration.parse(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static Map<String, String> propertiesMap(Properties properties) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            result.put(name, properties.getProperty(name));
        }
        return result;
    }
}
