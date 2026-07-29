package com.nowcoder.yierloom.core.plugin;

import java.util.List;
import java.util.Objects;

import com.nowcoder.yierloom.api.PluginConfig;
import com.nowcoder.yierloom.api.PluginDescriptor;
import com.nowcoder.yierloom.api.RuntimeCapability;
import com.nowcoder.yierloom.sdk.InstrumentationModule;

public record ValidatedPlugin(
        DiscoveredPlugin discovered,
        PluginDescriptor descriptor,
        PluginConfig config,
        RuntimeCapability runtimeCapability,
        List<InstrumentationModule> instrumentationModules
) {
    public ValidatedPlugin {
        discovered = Objects.requireNonNull(discovered, "discovered");
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        config = Objects.requireNonNull(config, "config");
        instrumentationModules = List.copyOf(
                Objects.requireNonNull(instrumentationModules, "instrumentationModules"));
    }

    public boolean hasRuntime() {
        return runtimeCapability != null;
    }

    public boolean hasInstrumentation() {
        return !instrumentationModules.isEmpty();
    }
}
