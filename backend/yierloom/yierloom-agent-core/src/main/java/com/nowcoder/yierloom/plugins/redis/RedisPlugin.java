package com.nowcoder.yierloom.plugins.redis;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import com.nowcoder.yierloom.api.PluginConfig;
import com.nowcoder.yierloom.api.PluginDescriptor;
import com.nowcoder.yierloom.api.PluginRuntimeContext;
import com.nowcoder.yierloom.api.RuntimeCapability;
import com.nowcoder.yierloom.api.YierLoomApi;
import com.nowcoder.yierloom.api.YierLoomPlugin;
import com.nowcoder.yierloom.plugins.support.PluginSettings;
import com.nowcoder.yierloom.plugins.support.dependency.DependencyRuntime;
import com.nowcoder.yierloom.sdk.InstrumentationCapability;
import com.nowcoder.yierloom.sdk.InstrumentationModule;

public final class RedisPlugin
        implements YierLoomPlugin, RuntimeCapability, InstrumentationCapability {
    private static final Set<String> CONFIG_KEYS = Set.of(
            "enabled",
            "sample-rate",
            "max-events-per-second",
            "summary-interval",
            "top-n",
            "max-tracked-keys",
            "slow-threshold");
    private static final List<String> DIMENSIONS = List.of(
            "redis.command",
            "redis.namespace.hash");

    private volatile DependencyRuntime runtime;

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(
                "redis", "Redis Diagnostics", "1.0.0", YierLoomApi.VERSION, false, 220);
    }

    @Override
    public List<InstrumentationModule> instrumentations(PluginConfig config) {
        RedisSettings.from(config);
        return List.of(new RedisInstrumentationModule());
    }

    @Override
    public synchronized void start(PluginRuntimeContext context) {
        if (runtime != null) {
            throw new IllegalStateException("redis plugin is already started");
        }
        RedisSettings settings = RedisSettings.from(context.config());
        runtime = DependencyRuntime.start(
                context,
                "redis",
                "redis_slow_call",
                "redis_call_summary",
                DIMENSIONS,
                settings.sampleRate(),
                settings.maxEventsPerSecond(),
                settings.summaryInterval(),
                settings.topN(),
                settings.maxTrackedKeys(),
                settings.slowThresholdMs());
    }

    @Override
    public synchronized void stop() {
        DependencyRuntime current = runtime;
        runtime = null;
        if (current != null) {
            current.close();
        }
    }

    record RedisSettings(
            double sampleRate,
            int maxEventsPerSecond,
            Duration summaryInterval,
            int topN,
            int maxTrackedKeys,
            long slowThresholdMs
    ) {
        static RedisSettings from(PluginConfig config) {
            PluginSettings.validateKeys(config, CONFIG_KEYS);
            return new RedisSettings(
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
