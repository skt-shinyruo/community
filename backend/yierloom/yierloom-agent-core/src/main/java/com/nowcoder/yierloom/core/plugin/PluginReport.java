package com.nowcoder.yierloom.core.plugin;

import java.nio.file.Path;
import java.util.Objects;

import com.nowcoder.yierloom.core.config.ConfigOrigin;

public record PluginReport(
        Path sourcePath,
        String pluginId,
        boolean enabled,
        PluginState state,
        String reasonCode,
        String detail
) {
    public PluginReport {
        sourcePath = Objects.requireNonNull(sourcePath, "sourcePath").normalize();
        pluginId = Objects.requireNonNull(pluginId, "pluginId");
        state = Objects.requireNonNull(state, "state");
        detail = detail == null ? "" : detail;
    }

    public String summary() {
        StringBuilder summary = new StringBuilder()
                .append("source=").append(sourcePath)
                .append(", plugin=").append(pluginId)
                .append(", enabled=").append(enabled)
                .append(", state=").append(state);
        if (reasonCode != null) {
            summary.append(", reason=").append(reasonCode);
        }
        if (!detail.isBlank()) {
            summary.append(", ").append(detail);
        }
        return summary.toString();
    }

    static PluginReport validated(DiscoveredPlugin discovered, String pluginId, boolean enabled) {
        return new PluginReport(
                discovered.sourcePath(), pluginId, enabled, PluginState.VALIDATED, null, "");
    }

    static PluginReport failed(
            DiscoveredPlugin discovered,
            String pluginId,
            boolean enabled,
            String reasonCode
    ) {
        return new PluginReport(
                discovered.sourcePath(), pluginId, enabled, PluginState.FAILED, reasonCode, "");
    }

    static PluginReport invalidConfig(
            DiscoveredPlugin discovered,
            String pluginId,
            boolean enabled,
            String key,
            ConfigOrigin origin
    ) {
        return new PluginReport(
                discovered.sourcePath(),
                pluginId,
                enabled,
                PluginState.FAILED,
                "INVALID_CONFIG",
                "key=" + key + ", origin=" + describe(origin));
    }

    static PluginReport lifecycle(
            ValidatedPlugin plugin,
            PluginState state,
            String reasonCode,
            String detail
    ) {
        return new PluginReport(
                plugin.discovered().sourcePath(),
                plugin.descriptor().id(),
                true,
                state,
                reasonCode,
                detail);
    }

    static String configDetail(String key, ConfigOrigin origin) {
        return "key=" + key + ", origin=" + describe(origin);
    }

    private static String describe(ConfigOrigin origin) {
        if (origin == null) {
            return "default";
        }
        return origin.source() + "@" + origin.location();
    }
}
