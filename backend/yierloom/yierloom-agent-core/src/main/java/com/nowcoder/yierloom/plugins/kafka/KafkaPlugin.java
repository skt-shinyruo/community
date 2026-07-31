package com.nowcoder.yierloom.plugins.kafka;

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

public final class KafkaPlugin
        implements YierLoomPlugin, RuntimeCapability, InstrumentationCapability {
    private static final Set<String> CONFIG_KEYS = Set.of(
            "enabled",
            "sample-rate",
            "max-events-per-second",
            "summary-interval",
            "top-n",
            "max-tracked-keys",
            "slow-threshold",
            "topic-names-enabled");
    private static final List<String> DIMENSIONS = List.of(
            "messaging.operation",
            "messaging.destination.name");

    private volatile DependencyRuntime runtime;

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(
                "kafka", "Kafka Diagnostics", "1.0.0", YierLoomApi.VERSION, false, 230);
    }

    @Override
    public List<InstrumentationModule> instrumentations(PluginConfig config) {
        KafkaSettings settings = KafkaSettings.from(config);
        return List.of(new KafkaInstrumentationModule(settings.topicNamesEnabled()));
    }

    @Override
    public synchronized void start(PluginRuntimeContext context) {
        if (runtime != null) {
            throw new IllegalStateException("kafka plugin is already started");
        }
        KafkaSettings settings = KafkaSettings.from(context.config());
        runtime = DependencyRuntime.start(
                context,
                "kafka",
                "kafka_slow_call",
                "kafka_produce_summary",
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

    record KafkaSettings(
            double sampleRate,
            int maxEventsPerSecond,
            Duration summaryInterval,
            int topN,
            int maxTrackedKeys,
            long slowThresholdMs,
            boolean topicNamesEnabled
    ) {
        static KafkaSettings from(PluginConfig config) {
            PluginSettings.validateKeys(config, CONFIG_KEYS);
            return new KafkaSettings(
                    PluginSettings.probability(config, "sample-rate", 1.0),
                    PluginSettings.nonNegativeInt(config, "max-events-per-second", 20),
                    PluginSettings.positiveDuration(
                            config, "summary-interval", Duration.ofSeconds(60)),
                    PluginSettings.positiveInt(config, "top-n", 50),
                    PluginSettings.positiveInt(config, "max-tracked-keys", 10_000),
                    PluginSettings.nonNegativeDurationMillis(
                            config, "slow-threshold", Duration.ofMillis(500)),
                    config.getBoolean("topic-names-enabled", false));
        }
    }
}
