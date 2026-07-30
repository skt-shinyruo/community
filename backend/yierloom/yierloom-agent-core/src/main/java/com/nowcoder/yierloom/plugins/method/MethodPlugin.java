package com.nowcoder.yierloom.plugins.method;

import java.time.Duration;
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

public final class MethodPlugin
        implements YierLoomPlugin, RuntimeCapability, InstrumentationCapability {
    private static final Set<String> CONFIG_KEYS = Set.of(
            "enabled",
            "includes",
            "excludes",
            "sample-rate",
            "max-events-per-second",
            "summary-interval",
            "top-n",
            "max-tracked-keys",
            "slow-threshold"
    );

    private volatile MethodRuntime runtime;

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(
                "method",
                "Method Diagnostics",
                "1.0.0",
                YierLoomApi.VERSION,
                true,
                100);
    }

    @Override
    public List<InstrumentationModule> instrumentations(PluginConfig config) {
        MethodSettings settings = MethodSettings.from(config);
        return List.of(new MethodInstrumentationModule(settings.matcher()));
    }

    @Override
    public synchronized void start(PluginRuntimeContext context) {
        if (runtime != null) {
            throw new IllegalStateException("method plugin is already started");
        }
        runtime = MethodRuntime.start(context, MethodSettings.from(context.config()));
    }

    @Override
    public synchronized void stop() {
        MethodRuntime current = runtime;
        runtime = null;
        if (current != null) {
            current.close();
        }
    }

    record MethodSettings(
            GlobClassMatcher matcher,
            double sampleRate,
            int maxEventsPerSecond,
            Duration summaryInterval,
            int topN,
            int maxTrackedKeys,
            long slowThresholdMs
    ) {
        static MethodSettings from(PluginConfig config) {
            PluginSettings.validateKeys(config, CONFIG_KEYS);
            return new MethodSettings(
                    new GlobClassMatcher(
                            PluginSettings.includes(config),
                            PluginSettings.excludes(config)),
                    PluginSettings.probability(config, "sample-rate", 1.0),
                    PluginSettings.nonNegativeInt(config, "max-events-per-second", 20),
                    PluginSettings.positiveDuration(
                            config, "summary-interval", Duration.ofSeconds(60)),
                    PluginSettings.positiveInt(config, "top-n", 50),
                    PluginSettings.positiveInt(config, "max-tracked-keys", 10_000),
                    PluginSettings.nonNegativeDurationMillis(
                            config, "slow-threshold", Duration.ofMillis(100)));
        }
    }
}
