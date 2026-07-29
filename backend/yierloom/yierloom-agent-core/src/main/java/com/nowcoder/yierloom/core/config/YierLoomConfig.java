package com.nowcoder.yierloom.core.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.nowcoder.yierloom.api.PluginConfig;
import com.nowcoder.yierloom.api.PluginDescriptor;

public record YierLoomConfig(
        boolean enabled,
        Optional<Path> pluginDirectory,
        int eventQueueCapacity,
        String serviceName,
        Map<String, PluginConfig> pluginConfigs,
        Map<String, ConfigOrigin> origins,
        List<String> warnings
) {
    public YierLoomConfig {
        pluginDirectory = Objects.requireNonNull(pluginDirectory);
        serviceName = Objects.requireNonNull(serviceName);
        pluginConfigs = Map.copyOf(Objects.requireNonNull(pluginConfigs));
        origins = Map.copyOf(Objects.requireNonNull(origins));
        warnings = List.copyOf(Objects.requireNonNull(warnings));
    }

    public PluginConfig pluginConfig(String pluginId) {
        return pluginConfigs.getOrDefault(pluginId, PluginConfig.empty());
    }

    public boolean pluginEnabled(PluginDescriptor descriptor) {
        Objects.requireNonNull(descriptor);
        return enabled && pluginConfig(descriptor.id()).getBoolean("enabled", descriptor.defaultEnabled());
    }

    public ConfigOrigin originForPluginKey(String pluginId, String key) {
        return origins.get("yierloom.plugins." + pluginId + "." + key);
    }
}
