package com.nowcoder.yierloom.plugins.jdbc;

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

public final class JdbcPlugin
        implements YierLoomPlugin, RuntimeCapability, InstrumentationCapability {
    private static final Set<String> CONFIG_KEYS = Set.of(
            "enabled",
            "sample-rate",
            "max-events-per-second",
            "summary-interval",
            "top-n",
            "max-tracked-keys",
            "slow-threshold");
    private static final List<String> DIMENSION_KEYS = List.of(
            "db.system",
            "db.operation",
            "db.statement.hash");

    private volatile DependencyRuntime runtime;

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(
                "jdbc",
                "JDBC Diagnostics",
                "1.0.0",
                YierLoomApi.VERSION,
                false,
                210);
    }

    @Override
    public List<InstrumentationModule> instrumentations(PluginConfig config) {
        JdbcSettings.from(config);
        return List.of(new JdbcInstrumentationModule());
    }

    @Override
    public synchronized void start(PluginRuntimeContext context) {
        if (runtime != null) {
            throw new IllegalStateException("jdbc plugin is already started");
        }
        JdbcSettings settings = JdbcSettings.from(context.config());
        runtime = DependencyRuntime.start(
                context,
                "jdbc",
                "jdbc_slow_call",
                "jdbc_call_summary",
                DIMENSION_KEYS,
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

    record JdbcSettings(
            double sampleRate,
            int maxEventsPerSecond,
            Duration summaryInterval,
            int topN,
            int maxTrackedKeys,
            long slowThresholdMs
    ) {
        static JdbcSettings from(PluginConfig config) {
            PluginSettings.validateKeys(config, CONFIG_KEYS);
            return new JdbcSettings(
                    PluginSettings.probability(config, "sample-rate", 1.0),
                    PluginSettings.nonNegativeInt(config, "max-events-per-second", 20),
                    PluginSettings.positiveDuration(
                            config, "summary-interval", Duration.ofSeconds(60)),
                    PluginSettings.positiveInt(config, "top-n", 50),
                    PluginSettings.positiveInt(config, "max-tracked-keys", 10_000),
                    PluginSettings.nonNegativeDurationMillis(
                            config, "slow-threshold", Duration.ofMillis(200)));
        }
    }
}
