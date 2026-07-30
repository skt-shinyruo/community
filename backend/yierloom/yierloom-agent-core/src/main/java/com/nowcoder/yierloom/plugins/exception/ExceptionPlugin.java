package com.nowcoder.yierloom.plugins.exception;

import java.util.List;
import java.util.Set;

import com.nowcoder.yierloom.api.PluginConfig;
import com.nowcoder.yierloom.api.PluginDescriptor;
import com.nowcoder.yierloom.api.PluginRuntimeContext;
import com.nowcoder.yierloom.api.RuntimeCapability;
import com.nowcoder.yierloom.api.YierLoomApi;
import com.nowcoder.yierloom.api.YierLoomPlugin;
import com.nowcoder.yierloom.plugins.support.GlobClassMatcher;
import com.nowcoder.yierloom.plugins.support.PluginSettings;
import com.nowcoder.yierloom.sdk.InstrumentationCapability;
import com.nowcoder.yierloom.sdk.InstrumentationModule;

public final class ExceptionPlugin
        implements YierLoomPlugin, RuntimeCapability, InstrumentationCapability {
    private static final Set<String> CONFIG_KEYS = Set.of(
            "enabled",
            "includes",
            "excludes",
            "sample-rate",
            "max-events-per-second",
            "max-tracked-keys"
    );

    private volatile ExceptionRuntime runtime;

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(
                "exception",
                "Exception Diagnostics",
                "1.0.0",
                YierLoomApi.VERSION,
                true,
                110);
    }

    @Override
    public List<InstrumentationModule> instrumentations(PluginConfig config) {
        ExceptionSettings settings = ExceptionSettings.from(config);
        return List.of(new ExceptionInstrumentationModule(settings.matcher()));
    }

    @Override
    public synchronized void start(PluginRuntimeContext context) {
        if (runtime != null) {
            throw new IllegalStateException("exception plugin is already started");
        }
        runtime = ExceptionRuntime.start(context, ExceptionSettings.from(context.config()));
    }

    @Override
    public synchronized void stop() {
        ExceptionRuntime current = runtime;
        runtime = null;
        if (current != null) {
            current.close();
        }
    }

    record ExceptionSettings(
            GlobClassMatcher matcher,
            double sampleRate,
            int maxEventsPerSecond,
            int maxTrackedKeys
    ) {
        static ExceptionSettings from(PluginConfig config) {
            PluginSettings.validateKeys(config, CONFIG_KEYS);
            return new ExceptionSettings(
                    new GlobClassMatcher(
                            PluginSettings.includes(config),
                            PluginSettings.excludes(config)),
                    PluginSettings.probability(config, "sample-rate", 1.0),
                    PluginSettings.nonNegativeInt(config, "max-events-per-second", 20),
                    PluginSettings.positiveInt(config, "max-tracked-keys", 10_000));
        }
    }
}
