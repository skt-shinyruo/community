package com.nowcoder.observability.runtimediagnostics.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class DiagnosticsConfigLoader {

    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("enabled", "false"),
            Map.entry("probes", "method,exception,thread,jvm"),
            Map.entry("includes", "*"),
            Map.entry("excludes", ""),
            Map.entry("sampleRate", "1.0"),
            Map.entry("maxEventsPerSecond", "20"),
            Map.entry("summaryInterval", "60s"),
            Map.entry("topN", "50"),
            Map.entry("maxTrackedKeys", "10000"),
            Map.entry("methodSlowThresholdMs", "100"),
            Map.entry("threadSnapshotInterval", "60s"),
            Map.entry("jvmSummaryInterval", "60s"),
            Map.entry("httpSlowThresholdMs", "500"),
            Map.entry("jdbcSlowThresholdMs", "200"),
            Map.entry("redisSlowThresholdMs", "100"),
            Map.entry("kafkaSlowThresholdMs", "500"),
            Map.entry("httpSampleRate", "1.0"),
            Map.entry("jdbcSampleRate", "1.0"),
            Map.entry("redisSampleRate", "1.0"),
            Map.entry("kafkaSampleRate", "1.0"),
            Map.entry("httpMaxEventsPerSecond", "20"),
            Map.entry("jdbcMaxEventsPerSecond", "20"),
            Map.entry("redisMaxEventsPerSecond", "20"),
            Map.entry("kafkaMaxEventsPerSecond", "20"),
            Map.entry("kafkaTopicNamesEnabled", "false")
    );

    private DiagnosticsConfigLoader() {
    }

    public static DiagnosticsConfig load(String agentArgs) {
        return load(agentArgs, propertiesMap(System.getProperties()), System.getenv());
    }

    public static DiagnosticsConfig load(
            String agentArgs,
            Map<String, String> systemProperties,
            Map<String, String> environment
    ) {
        Map<String, String> values = new LinkedHashMap<>(DEFAULTS);
        applyOverrides(values, environment, "runtime_diagnostics_");
        applyOverrides(values, systemProperties, "runtime.diagnostics.");
        parseAgentArgs(agentArgs).forEach(values::put);

        return new DiagnosticsConfig(
                Boolean.parseBoolean(values.get("enabled")),
                listValues(values.get("probes")),
                listValues(values.get("includes")),
                listValues(values.get("excludes")),
                parseDouble(values.get("sampleRate"), 1.0),
                parseInt(values.get("maxEventsPerSecond"), 20),
                parseDuration(values.get("summaryInterval"), Duration.ofSeconds(60)),
                parseInt(values.get("topN"), 50),
                parseInt(values.get("maxTrackedKeys"), 10_000),
                parseLong(values.get("methodSlowThresholdMs"), 100),
                parseDuration(values.get("threadSnapshotInterval"), Duration.ofSeconds(60)),
                parseDuration(values.get("jvmSummaryInterval"), Duration.ofSeconds(60)),
                parseLong(values.get("httpSlowThresholdMs"), 500),
                parseLong(values.get("jdbcSlowThresholdMs"), 200),
                parseLong(values.get("redisSlowThresholdMs"), 100),
                parseLong(values.get("kafkaSlowThresholdMs"), 500),
                parseDouble(values.get("httpSampleRate"), 1.0),
                parseDouble(values.get("jdbcSampleRate"), 1.0),
                parseDouble(values.get("redisSampleRate"), 1.0),
                parseDouble(values.get("kafkaSampleRate"), 1.0),
                parseInt(values.get("httpMaxEventsPerSecond"), 20),
                parseInt(values.get("jdbcMaxEventsPerSecond"), 20),
                parseInt(values.get("redisMaxEventsPerSecond"), 20),
                parseInt(values.get("kafkaMaxEventsPerSecond"), 20),
                Boolean.parseBoolean(values.get("kafkaTopicNamesEnabled"))
        );
    }

    private static void applyOverrides(Map<String, String> values, Map<String, String> overrides, String requiredPrefix) {
        if (overrides == null || overrides.isEmpty()) {
            return;
        }
        overrides.forEach((key, value) -> {
            if (key == null || !key.trim().toLowerCase(Locale.ROOT).startsWith(requiredPrefix)) {
                return;
            }
            String normalizedKey = normalizeKey(key);
            if (!normalizedKey.isEmpty() && value != null && (!value.isBlank() || acceptsBlankOverride(normalizedKey))) {
                values.put(normalizedKey, value.trim());
            }
        });
    }

    private static Map<String, String> parseAgentArgs(String agentArgs) {
        Map<String, String> result = new LinkedHashMap<>();
        if (agentArgs == null || agentArgs.isBlank()) {
            return result;
        }
        String currentKey = null;
        for (String pair : agentArgs.split(",")) {
            int index = pair.indexOf('=');
            if (index <= 0) {
                if (currentKey != null && acceptsCsvContinuation(currentKey) && !pair.isBlank()) {
                    result.compute(currentKey, (key, value) -> value == null || value.isBlank()
                            ? pair.trim()
                            : value + "," + pair.trim());
                }
                continue;
            }
            String key = normalizeKey(pair.substring(0, index));
            String value = pair.substring(index + 1).trim();
            if (!key.isEmpty() && (!value.isBlank() || acceptsBlankOverride(key))) {
                result.put(key, value);
                currentKey = key;
            } else {
                currentKey = null;
            }
        }
        return result;
    }

    private static boolean acceptsCsvContinuation(String key) {
        return "probes".equals(key) || "includes".equals(key) || "excludes".equals(key);
    }

    private static boolean acceptsBlankOverride(String key) {
        return "probes".equals(key) || "includes".equals(key) || "excludes".equals(key);
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("runtime.diagnostics.")) {
            normalized = normalized.substring("runtime.diagnostics.".length());
        } else if (normalized.startsWith("runtime_diagnostics_")) {
            normalized = normalized.substring("runtime_diagnostics_".length());
        }
        String compact = normalized.replace("_", "").replace("-", "");
        return switch (compact) {
            case "enabled" -> "enabled";
            case "probes" -> "probes";
            case "includes" -> "includes";
            case "excludes" -> "excludes";
            case "samplerate" -> "sampleRate";
            case "maxeventspersecond" -> "maxEventsPerSecond";
            case "summaryinterval" -> "summaryInterval";
            case "topn" -> "topN";
            case "maxtrackedkeys" -> "maxTrackedKeys";
            case "methodslowthresholdms" -> "methodSlowThresholdMs";
            case "threadsnapshotinterval" -> "threadSnapshotInterval";
            case "jvmsummaryinterval" -> "jvmSummaryInterval";
            case "httpslowthresholdms" -> "httpSlowThresholdMs";
            case "jdbcslowthresholdms" -> "jdbcSlowThresholdMs";
            case "redisslowthresholdms" -> "redisSlowThresholdMs";
            case "kafkaslowthresholdms" -> "kafkaSlowThresholdMs";
            case "httpsamplerate" -> "httpSampleRate";
            case "jdbcsamplerate" -> "jdbcSampleRate";
            case "redissamplerate" -> "redisSampleRate";
            case "kafkasamplerate" -> "kafkaSampleRate";
            case "httpmaxeventspersecond" -> "httpMaxEventsPerSecond";
            case "jdbcmaxeventspersecond" -> "jdbcMaxEventsPerSecond";
            case "redismaxeventspersecond" -> "redisMaxEventsPerSecond";
            case "kafkamaxeventspersecond" -> "kafkaMaxEventsPerSecond";
            case "kafkatopicnamesenabled" -> "kafkaTopicNamesEnabled";
            default -> "";
        };
    }

    private static List<String> listValues(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[,;]"))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? parsed : fallback;
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
            if (normalized.matches("\\d+ms")) {
                return Duration.ofMillis(Long.parseLong(normalized.substring(0, normalized.length() - 2)));
            }
            if (normalized.matches("\\d+s")) {
                return Duration.ofSeconds(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
            }
            if (normalized.matches("\\d+m")) {
                return Duration.ofMinutes(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
            }
            if (normalized.matches("\\d+")) {
                return Duration.ofSeconds(Long.parseLong(normalized));
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
