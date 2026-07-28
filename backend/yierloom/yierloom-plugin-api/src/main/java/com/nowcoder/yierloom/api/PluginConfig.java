package com.nowcoder.yierloom.api;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;

public final class PluginConfig {
    private static final PluginConfig EMPTY = new PluginConfig(Map.of());

    private final Map<String, String> values;

    private PluginConfig(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    public static PluginConfig empty() {
        return EMPTY;
    }

    public static PluginConfig of(Map<String, String> values) {
        return new PluginConfig(Objects.requireNonNull(values));
    }

    public Set<String> keys() {
        return values.keySet();
    }

    public Optional<String> findString(String key) {
        return find(key, "string", Function.identity());
    }

    public String requireString(String key) {
        return findString(key).orElseThrow(() -> missing(key, "string"));
    }

    public String getString(String key, String defaultValue) {
        return findString(key).orElse(Objects.requireNonNull(defaultValue));
    }

    public Optional<Boolean> findBoolean(String key) {
        return find(key, "boolean", value -> {
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
            throw malformed(key, "boolean");
        });
    }

    public boolean requireBoolean(String key) {
        return findBoolean(key).orElseThrow(() -> missing(key, "boolean"));
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return findBoolean(key).orElse(defaultValue);
    }

    public OptionalInt findInt(String key) {
        Optional<Integer> value = find(key, "integer", raw -> parseInt(key, raw));
        return value.isPresent() ? OptionalInt.of(value.get()) : OptionalInt.empty();
    }

    public int requireInt(String key) {
        OptionalInt value = findInt(key);
        if (value.isEmpty()) {
            throw missing(key, "integer");
        }
        return value.getAsInt();
    }

    public int getInt(String key, int defaultValue) {
        OptionalInt value = findInt(key);
        return value.orElse(defaultValue);
    }

    public OptionalLong findLong(String key) {
        Optional<Long> value = find(key, "long", raw -> parseLong(key, raw));
        return value.isPresent() ? OptionalLong.of(value.get()) : OptionalLong.empty();
    }

    public long requireLong(String key) {
        OptionalLong value = findLong(key);
        if (value.isEmpty()) {
            throw missing(key, "long");
        }
        return value.getAsLong();
    }

    public long getLong(String key, long defaultValue) {
        return findLong(key).orElse(defaultValue);
    }

    public OptionalDouble findDouble(String key) {
        Optional<Double> value = find(key, "double", raw -> parseDouble(key, raw));
        return value.isPresent() ? OptionalDouble.of(value.get()) : OptionalDouble.empty();
    }

    public double requireDouble(String key) {
        OptionalDouble value = findDouble(key);
        if (value.isEmpty()) {
            throw missing(key, "double");
        }
        return value.getAsDouble();
    }

    public double getDouble(String key, double defaultValue) {
        return findDouble(key).orElse(defaultValue);
    }

    public Optional<Duration> findDuration(String key) {
        return find(key, "duration", raw -> parseDuration(key, raw));
    }

    public Duration requireDuration(String key) {
        return findDuration(key).orElseThrow(() -> missing(key, "duration"));
    }

    public Duration getDuration(String key, Duration defaultValue) {
        return findDuration(key).orElse(Objects.requireNonNull(defaultValue));
    }

    public Optional<List<String>> findStringList(String key) {
        return find(key, "string list", PluginConfig::parseStringList);
    }

    public List<String> requireStringList(String key) {
        return findStringList(key).orElseThrow(() -> missing(key, "string list"));
    }

    public List<String> getStringList(String key, List<String> defaultValue) {
        return findStringList(key).orElseGet(() -> List.copyOf(Objects.requireNonNull(defaultValue)));
    }

    private <T> Optional<T> find(String key, String expectedType, Function<String, T> parser) {
        Objects.requireNonNull(key);
        String value = values.get(key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(parser.apply(value));
        } catch (PluginConfigurationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw malformed(key, expectedType);
        }
    }

    private static int parseInt(String key, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw malformed(key, "integer");
        }
    }

    private static long parseLong(String key, String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw malformed(key, "long");
        }
    }

    private static double parseDouble(String key, String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw malformed(key, "double");
        }
    }

    private static Duration parseDuration(String key, String value) {
        try {
            if (value.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(value.substring(0, value.length() - 2)));
            }
            if (value.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(value.substring(0, value.length() - 1)));
            }
            if (value.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(value.substring(0, value.length() - 1)));
            }
            if (value.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(value.substring(0, value.length() - 1)));
            }
            if (value.startsWith("P")) {
                return Duration.parse(value);
            }
            return Duration.ofSeconds(Long.parseLong(value));
        } catch (DateTimeParseException | NumberFormatException | ArithmeticException exception) {
            throw malformed(key, "duration");
        }
    }

    private static List<String> parseStringList(String value) {
        if (value.isEmpty()) {
            return List.of();
        }
        String[] entries = value.split(",", -1);
        List<String> result = new ArrayList<>(entries.length);
        for (String entry : entries) {
            result.add(entry.trim());
        }
        return List.copyOf(result);
    }

    private static PluginConfigurationException missing(String key, String expectedType) {
        return new PluginConfigurationException(key, expectedType);
    }

    private static PluginConfigurationException malformed(String key, String expectedType) {
        return new PluginConfigurationException(key, expectedType);
    }
}
