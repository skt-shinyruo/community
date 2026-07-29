package com.nowcoder.yierloom.core.plugin;

import java.nio.file.Path;
import java.util.Objects;

import com.nowcoder.yierloom.api.PluginDescriptor;
import com.nowcoder.yierloom.api.YierLoomPlugin;

public record DiscoveredPlugin(
        YierLoomPlugin provider,
        PluginSource source,
        Path sourcePath
) {
    public DiscoveredPlugin {
        provider = Objects.requireNonNull(provider, "provider");
        source = Objects.requireNonNull(source, "source");
        sourcePath = Objects.requireNonNull(sourcePath, "sourcePath").normalize();
    }

    public PluginDescriptor descriptor() {
        return provider.descriptor();
    }
}
