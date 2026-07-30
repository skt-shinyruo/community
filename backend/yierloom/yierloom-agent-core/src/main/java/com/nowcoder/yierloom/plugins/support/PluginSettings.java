package com.nowcoder.yierloom.plugins.support;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.nowcoder.yierloom.api.PluginConfig;
import com.nowcoder.yierloom.api.PluginConfigurationException;

public final class PluginSettings {
    private PluginSettings() {
    }

    public static void validateKeys(PluginConfig config, Set<String> allowedKeys) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(allowedKeys, "allowedKeys");
        config.keys().stream()
                .filter(key -> !allowedKeys.contains(key))
                .sorted()
                .findFirst()
                .ifPresent(key -> {
                    throw invalid(key, "a supported key");
                });
        config.findBoolean("enabled");
    }

    public static List<String> includes(PluginConfig config) {
        List<String> includes = normalizedList(config.getStringList("includes", List.of("*")));
        return includes.isEmpty() ? List.of("*") : includes;
    }

    public static List<String> excludes(PluginConfig config) {
        return normalizedList(config.getStringList("excludes", List.of()));
    }

    public static double probability(PluginConfig config, String key, double defaultValue) {
        double value = config.getDouble(key, defaultValue);
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw invalid(key, "a finite number from 0.0 through 1.0");
        }
        return value;
    }

    public static int nonNegativeInt(PluginConfig config, String key, int defaultValue) {
        int value = config.getInt(key, defaultValue);
        if (value < 0) {
            throw invalid(key, "a non-negative integer");
        }
        return value;
    }

    public static int positiveInt(PluginConfig config, String key, int defaultValue) {
        int value = config.getInt(key, defaultValue);
        if (value < 1) {
            throw invalid(key, "a positive integer");
        }
        return value;
    }

    public static Duration positiveDuration(
            PluginConfig config,
            String key,
            Duration defaultValue
    ) {
        Duration value = config.getDuration(key, defaultValue);
        if (value.isZero() || value.isNegative()) {
            throw invalid(key, "a positive duration");
        }
        return value;
    }

    public static long nonNegativeDurationMillis(
            PluginConfig config,
            String key,
            Duration defaultValue
    ) {
        Duration value = config.getDuration(key, defaultValue);
        if (value.isNegative()) {
            throw invalid(key, "a non-negative duration");
        }
        try {
            return value.toMillis();
        } catch (ArithmeticException failure) {
            throw invalid(key, "a non-negative millisecond duration");
        }
    }

    private static List<String> normalizedList(List<String> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static PluginConfigurationException invalid(String key, String expectedType) {
        return new PluginConfigurationException(key, expectedType);
    }
}
