package com.nowcoder.yierloom.core.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import com.nowcoder.yierloom.api.PluginConfig;

public final class YierLoomConfigLoader {
    private static final String CONFIG_KEY = "yierloom.config";
    private static final String ENABLED_KEY = "yierloom.enabled";
    private static final String PLUGIN_DIRECTORY_KEY = "yierloom.plugins.dir";
    private static final String QUEUE_CAPACITY_KEY = "yierloom.events.queue-capacity";
    private static final String SERVICE_NAME_KEY = "yierloom.service.name";
    private static final String OTEL_SERVICE_NAME_KEY = "otel.service.name";
    private static final String PLUGIN_PREFIX = "yierloom.plugins.";
    private static final String PLUGIN_ENV_PREFIX = "YIERLOOM_PLUGIN__";
    private static final int DEFAULT_QUEUE_CAPACITY = 8192;
    private static final Set<String> KNOWN_GLOBAL_KEYS = Set.of(
            CONFIG_KEY,
            ENABLED_KEY,
            PLUGIN_DIRECTORY_KEY,
            QUEUE_CAPACITY_KEY,
            SERVICE_NAME_KEY);

    private YierLoomConfigLoader() {
    }

    public static YierLoomConfig load(
            String agentArguments,
            Map<String, String> systemProperties,
            Map<String, String> environment,
            Path userDirectory
    ) {
        Objects.requireNonNull(systemProperties);
        Objects.requireNonNull(environment);
        Objects.requireNonNull(userDirectory);

        SourceValues agent = parseAgentArguments(agentArguments);
        SourceValues env = normalizeEnvironment(environment);
        SourceValues system = directSource(systemProperties);
        Optional<Path> configFile = resolveConfigFile(env, system, agent, userDirectory);
        SourceValues file = configFile.map(YierLoomConfigLoader::loadProperties).orElseGet(SourceValues::empty);

        Map<String, ResolvedValue> merged = new LinkedHashMap<>();
        merge(merged, file, ConfigSource.FILE);
        merge(merged, env, ConfigSource.ENVIRONMENT);
        merge(merged, system, ConfigSource.SYSTEM_PROPERTY);
        merge(merged, agent, ConfigSource.AGENT_ARGUMENT);

        boolean enabled = readBoolean(merged, ENABLED_KEY, false);
        int queueCapacity = readPositiveInt(merged, QUEUE_CAPACITY_KEY, DEFAULT_QUEUE_CAPACITY);
        Optional<Path> pluginDirectory = resolvePluginDirectory(merged, userDirectory);
        String serviceName = resolveServiceName(merged, environment);
        Map<String, PluginConfig> pluginConfigs = buildPluginConfigs(merged);
        Map<String, ConfigOrigin> origins = buildOrigins(merged);
        List<String> warnings = findWarnings(merged);

        return new YierLoomConfig(
                enabled,
                pluginDirectory,
                queueCapacity,
                serviceName,
                pluginConfigs,
                origins,
                warnings);
    }

    private static SourceValues parseAgentArguments(String agentArguments) {
        if (agentArguments == null || agentArguments.isBlank()) {
            return SourceValues.empty();
        }
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, String> locations = new LinkedHashMap<>();
        for (String entry : agentArguments.split(",", -1)) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException("invalid YierLoom agent argument");
            }
            String rawKey = trimmed.substring(0, separator).trim();
            String key = switch (rawKey) {
                case "config" -> CONFIG_KEY;
                case "plugins-dir" -> PLUGIN_DIRECTORY_KEY;
                default -> rawKey;
            };
            if (key.isEmpty()) {
                throw new IllegalArgumentException("invalid YierLoom agent argument");
            }
            values.put(key, trimmed.substring(separator + 1).trim());
            locations.put(key, rawKey);
        }
        return new SourceValues(values, locations);
    }

    private static SourceValues normalizeEnvironment(Map<String, String> environment) {
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, String> locations = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            String key = normalizeEnvironmentKey(entry.getKey());
            if (key != null) {
                values.put(key, entry.getValue());
                locations.put(key, entry.getKey());
            }
        }
        return new SourceValues(values, locations);
    }

    private static String normalizeEnvironmentKey(String environmentKey) {
        if (environmentKey.startsWith(PLUGIN_ENV_PREFIX)) {
            String remainder = environmentKey.substring(PLUGIN_ENV_PREFIX.length());
            int boundary = remainder.indexOf("__");
            if (boundary <= 0 || boundary == remainder.length() - 2) {
                return null;
            }
            String pluginId = toKebabCase(remainder.substring(0, boundary));
            String pluginKey = toKebabCase(remainder.substring(boundary + 2));
            return PLUGIN_PREFIX + pluginId + "." + pluginKey;
        }
        return switch (environmentKey) {
            case "YIERLOOM_CONFIG" -> CONFIG_KEY;
            case "YIERLOOM_ENABLED" -> ENABLED_KEY;
            case "YIERLOOM_PLUGINS_DIR" -> PLUGIN_DIRECTORY_KEY;
            case "YIERLOOM_EVENTS_QUEUE_CAPACITY" -> QUEUE_CAPACITY_KEY;
            case "YIERLOOM_SERVICE_NAME" -> SERVICE_NAME_KEY;
            default -> environmentKey.startsWith("YIERLOOM_")
                    ? "yierloom." + toKebabCase(environmentKey.substring("YIERLOOM_".length()))
                    : null;
        };
    }

    private static String toKebabCase(String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static SourceValues directSource(Map<String, String> source) {
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, String> locations = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            values.put(Objects.requireNonNull(key), Objects.requireNonNull(value));
            locations.put(key, key);
        });
        return new SourceValues(values, locations);
    }

    private static Optional<Path> resolveConfigFile(
            SourceValues environment,
            SourceValues system,
            SourceValues agent,
            Path userDirectory
    ) {
        String configured = environment.values().get(CONFIG_KEY);
        if (system.values().containsKey(CONFIG_KEY)) {
            configured = system.values().get(CONFIG_KEY);
        }
        if (agent.values().containsKey(CONFIG_KEY)) {
            configured = agent.values().get(CONFIG_KEY);
        }
        if (configured == null) {
            return Optional.empty();
        }
        if (configured.isBlank()) {
            throw invalidGlobal(CONFIG_KEY, "path");
        }
        Path path = resolvePath(configured, userDirectory, CONFIG_KEY);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException("invalid global configuration key '" + CONFIG_KEY + "', unreadable file");
        }
        return Optional.of(path);
    }

    private static SourceValues loadProperties(Path path) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException exception) {
            throw new IllegalArgumentException("unable to load global configuration key '" + CONFIG_KEY + "'", exception);
        }
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, String> locations = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            values.put(key, properties.getProperty(key));
            locations.put(key, path.toString());
        }
        return new SourceValues(values, locations);
    }

    private static void merge(
            Map<String, ResolvedValue> target,
            SourceValues source,
            ConfigSource configSource
    ) {
        source.values().forEach((key, value) -> target.put(
                key,
                new ResolvedValue(
                        Objects.requireNonNull(value),
                        new ConfigOrigin(configSource, source.locations().get(key)))));
    }

    private static boolean readBoolean(Map<String, ResolvedValue> values, String key, boolean defaultValue) {
        ResolvedValue resolved = values.get(key);
        if (resolved == null) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(resolved.value())) {
            return true;
        }
        if ("false".equalsIgnoreCase(resolved.value())) {
            return false;
        }
        throw invalidGlobal(key, "boolean");
    }

    private static int readPositiveInt(Map<String, ResolvedValue> values, String key, int defaultValue) {
        ResolvedValue resolved = values.get(key);
        if (resolved == null) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(resolved.value());
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
        }
        throw invalidGlobal(key, "positive integer");
    }

    private static Optional<Path> resolvePluginDirectory(
            Map<String, ResolvedValue> values,
            Path userDirectory
    ) {
        ResolvedValue resolved = values.get(PLUGIN_DIRECTORY_KEY);
        if (resolved == null) {
            return Optional.empty();
        }
        if (resolved.value().isBlank()) {
            throw invalidGlobal(PLUGIN_DIRECTORY_KEY, "path");
        }
        return Optional.of(resolvePath(resolved.value(), userDirectory, PLUGIN_DIRECTORY_KEY));
    }

    private static Path resolvePath(String value, Path userDirectory, String key) {
        Path path;
        try {
            path = Path.of(value);
        } catch (RuntimeException exception) {
            throw invalidGlobal(key, "path");
        }
        return path.isAbsolute() ? path.normalize() : userDirectory.resolve(path).normalize();
    }

    private static String resolveServiceName(
            Map<String, ResolvedValue> values,
            Map<String, String> environment
    ) {
        String serviceName = valueIfNotBlank(values.get(SERVICE_NAME_KEY));
        if (serviceName == null) {
            serviceName = valueIfNotBlank(values.get(OTEL_SERVICE_NAME_KEY));
        }
        if (serviceName == null) {
            serviceName = nonBlank(environment.get("OTEL_SERVICE_NAME"));
        }
        if (serviceName == null) {
            serviceName = nonBlank(environment.get("SERVICE_NAME"));
        }
        return serviceName == null ? "unknown" : serviceName;
    }

    private static String valueIfNotBlank(ResolvedValue value) {
        return value == null ? null : nonBlank(value.value());
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Map<String, PluginConfig> buildPluginConfigs(Map<String, ResolvedValue> values) {
        Map<String, Map<String, String>> grouped = new LinkedHashMap<>();
        values.forEach((key, resolved) -> {
            if (!key.startsWith(PLUGIN_PREFIX)) {
                return;
            }
            String remainder = key.substring(PLUGIN_PREFIX.length());
            int boundary = remainder.indexOf('.');
            if (boundary <= 0 || boundary == remainder.length() - 1) {
                return;
            }
            grouped.computeIfAbsent(remainder.substring(0, boundary), ignored -> new LinkedHashMap<>())
                    .put(remainder.substring(boundary + 1), resolved.value());
        });
        Map<String, PluginConfig> result = new LinkedHashMap<>();
        grouped.forEach((pluginId, config) -> result.put(pluginId, PluginConfig.of(config)));
        return Map.copyOf(result);
    }

    private static Map<String, ConfigOrigin> buildOrigins(Map<String, ResolvedValue> values) {
        Map<String, ConfigOrigin> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(key, value.origin()));
        return Map.copyOf(result);
    }

    private static List<String> findWarnings(Map<String, ResolvedValue> values) {
        List<String> warnings = new ArrayList<>();
        for (String key : values.keySet()) {
            if (key.startsWith("yierloom.")
                    && !KNOWN_GLOBAL_KEYS.contains(key)
                    && !isPluginPrivateKey(key)) {
                warnings.add("unknown YierLoom configuration key '" + key + "'");
            }
        }
        return List.copyOf(warnings);
    }

    private static boolean isPluginPrivateKey(String key) {
        if (!key.startsWith(PLUGIN_PREFIX)) {
            return false;
        }
        String remainder = key.substring(PLUGIN_PREFIX.length());
        int boundary = remainder.indexOf('.');
        return boundary > 0 && boundary < remainder.length() - 1;
    }

    private static IllegalArgumentException invalidGlobal(String key, String expectedType) {
        return new IllegalArgumentException(
                "invalid global configuration key '" + key + "', expected " + expectedType);
    }

    private record SourceValues(Map<String, String> values, Map<String, String> locations) {
        private SourceValues {
            values = Map.copyOf(values);
            locations = Map.copyOf(locations);
        }

        private static SourceValues empty() {
            return new SourceValues(Map.of(), Map.of());
        }
    }

    private record ResolvedValue(String value, ConfigOrigin origin) {
    }
}
