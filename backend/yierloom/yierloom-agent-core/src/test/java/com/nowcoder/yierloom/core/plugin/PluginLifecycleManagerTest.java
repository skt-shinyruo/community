package com.nowcoder.yierloom.core.plugin;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.nowcoder.yierloom.api.DiagnosticEvent;
import com.nowcoder.yierloom.api.PluginConfig;
import com.nowcoder.yierloom.api.PluginConfigurationException;
import com.nowcoder.yierloom.api.PluginDescriptor;
import com.nowcoder.yierloom.api.PluginRuntimeContext;
import com.nowcoder.yierloom.api.RuntimeCapability;
import com.nowcoder.yierloom.api.YierLoomPlugin;
import com.nowcoder.yierloom.core.config.ConfigOrigin;
import com.nowcoder.yierloom.core.config.ConfigSource;
import com.nowcoder.yierloom.core.config.YierLoomConfig;
import com.nowcoder.yierloom.core.event.YierLoomEventPipeline;
import com.nowcoder.yierloom.core.instrumentation.PluginInstrumentationController;
import com.nowcoder.yierloom.core.runtime.ManagedSchedulerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginLifecycleManagerTest {
    private final List<String> startCalls = new ArrayList<>();
    private final List<String> stopCalls = new ArrayList<>();
    private YierLoomEventPipeline pipeline;
    private ManagedSchedulerRegistry schedulers;
    private PluginLifecycleManager manager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        pipeline = new YierLoomEventPipeline(
                16,
                "test-service",
                Clock.systemUTC(),
                new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8));
        schedulers = new ManagedSchedulerRegistry();
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.stopRuntimesInReverseOrder();
            manager.cancelManagedTasks();
            manager.removeTransformers();
            manager.closeExternalLoaders();
        } else {
            schedulers.close();
        }
    }

    @Test
    void startsByDescriptorOrderAndRollsBackOnlyTheFailingPlugin() {
        RecordingInstrumentationController instrumentation = new RecordingInstrumentationController("beta");
        manager = manager(instrumentation, config(Map.of(), Map.of()));

        manager.activate(List.of(validated("gamma", 30), validated("beta", 20), validated("alpha", 20)));

        assertThat(startCalls).containsExactly("alpha", "beta", "gamma");
        assertThat(stopCalls).contains("beta");
        assertThat(instrumentation.removedPluginIds()).containsExactly("beta");
        assertThat(manager.activePluginIds()).containsExactly("alpha", "gamma");
        assertThat(pipeline.emit("beta", DiagnosticEvent.builder("after-rollback").build())).isFalse();
        assertThat(schedulers.taskCount("beta")).isZero();
        assertThat(manager.reports()).filteredOn(report -> "beta".equals(report.pluginId()))
                .extracting(PluginReport::reasonCode).containsExactly("ACTIVATION_FAILED");
    }

    @Test
    void compensationContinuesWhenIndividualCleanupActionsFail() {
        RecordingInstrumentationController instrumentation = new RecordingInstrumentationController("beta");
        instrumentation.failRemoval = true;
        manager = manager(instrumentation, config(Map.of(), Map.of()));
        ValidatedPlugin beta = validated(new LifecyclePlugin("beta", 0, true, false));

        manager.activate(List.of(beta));

        assertThat(stopCalls).containsExactly("beta");
        assertThat(schedulers.taskCount("beta")).isZero();
        assertThat(pipeline.emit("beta", DiagnosticEvent.builder("after-rollback").build())).isFalse();
        assertThat(manager.activePluginIds()).isEmpty();
    }

    @Test
    void stopsRuntimesInReverseOrderAndContinuesAfterOrdinaryFailures() {
        RecordingInstrumentationController instrumentation = new RecordingInstrumentationController(null);
        manager = manager(instrumentation, config(Map.of(), Map.of()));
        ValidatedPlugin alpha = validated(new LifecyclePlugin("alpha", 10, false, false));
        ValidatedPlugin beta = validated(new LifecyclePlugin("beta", 20, false, true));
        manager.activate(List.of(alpha, beta));

        manager.stopRuntimesInReverseOrder();

        assertThat(stopCalls).containsExactly("beta", "alpha");
        assertThat(manager.activePluginIds()).isEmpty();
        assertThat(manager.reports()).filteredOn(report -> "beta".equals(report.pluginId()))
                .extracting(PluginReport::reasonCode).containsExactly("RUNTIME_STOP_FAILED");
    }

    @Test
    void lifecycleConfigurationFailureReportsOnlyKeyAndOrigin() {
        RecordingInstrumentationController instrumentation = new RecordingInstrumentationController(null);
        ConfigOrigin origin = new ConfigOrigin(ConfigSource.ENVIRONMENT, "environment");
        YierLoomConfig config = config(
                Map.of("configured", PluginConfig.of(Map.of("token", "private-token"))),
                Map.of("yierloom.plugins.configured.token", origin));
        manager = manager(instrumentation, config);
        ValidatedPlugin plugin = validated(new LifecyclePlugin("configured", 0, false, false) {
            @Override
            public void start(PluginRuntimeContext context) {
                throw new PluginConfigurationException("token", "duration");
            }
        }, config.pluginConfig("configured"));

        manager.activate(List.of(plugin));

        assertThat(manager.reports()).singleElement().satisfies(report -> {
            assertThat(report.reasonCode()).isEqualTo("INVALID_CONFIG");
            assertThat(report.summary()).contains("token", "environment");
            assertThat(report.summary()).doesNotContain("private-token");
        });
    }

    @Test
    void fatalActivationErrorIsRethrownAfterRollback() {
        RecordingInstrumentationController instrumentation = new RecordingInstrumentationController(null);
        manager = manager(instrumentation, config(Map.of(), Map.of()));
        ValidatedPlugin plugin = validated(new LifecyclePlugin("fatal", 0, false, false) {
            @Override
            public void start(PluginRuntimeContext context) {
                throw new OutOfMemoryError("fatal");
            }
        });

        assertThatThrownBy(() -> manager.activate(List.of(plugin)))
                .isInstanceOf(OutOfMemoryError.class);
        assertThat(stopCalls).containsExactly("fatal");
        assertThat(manager.activePluginIds()).isEmpty();
        assertThat(pipeline.emit("fatal", DiagnosticEvent.builder("after-fatal").build())).isFalse();
    }

    @Test
    void splitShutdownOperationsAreIdempotent() {
        RecordingInstrumentationController instrumentation = new RecordingInstrumentationController(null);
        manager = manager(instrumentation, config(Map.of(), Map.of()));
        manager.activate(List.of(validated("alpha", 0)));

        manager.stopRuntimesInReverseOrder();
        manager.stopRuntimesInReverseOrder();
        manager.cancelManagedTasks();
        manager.cancelManagedTasks();
        manager.removeTransformers();
        manager.removeTransformers();
        manager.closeExternalLoaders();
        manager.closeExternalLoaders();

        assertThat(stopCalls).containsExactly("alpha");
        assertThat(instrumentation.removeAllCalls).hasValue(1);
    }

    @Test
    void retainsExternalLoadersUntilTransformerRemovalSucceeds() throws Exception {
        Path jar = PluginJarFixture.jarWithPrivateCopies(tempDir);
        YierLoomPluginClassLoader loader = new YierLoomPluginClassLoader(
                jar, getClass().getClassLoader());
        RecordingInstrumentationController instrumentation = new RecordingInstrumentationController(null);
        instrumentation.removeAllFailures = 1;
        manager = new PluginLifecycleManager(
                config(Map.of(), Map.of()),
                pipeline,
                schedulers,
                instrumentation,
                List.of(loader),
                Clock.systemUTC());

        manager.removeTransformers();
        manager.closeExternalLoaders();
        assertThat(loader.loadClass("fixture.privatecopy.One")).isNotNull();

        manager.removeTransformers();
        manager.closeExternalLoaders();
        manager.closeExternalLoaders();

        assertThatThrownBy(() -> loader.loadClass("fixture.privatecopy.Two"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThat(instrumentation.removeAllCalls).hasValue(2);
    }

    private PluginLifecycleManager manager(
            RecordingInstrumentationController instrumentation,
            YierLoomConfig config
    ) {
        return new PluginLifecycleManager(
                config,
                pipeline,
                schedulers,
                instrumentation,
                List.of(),
                Clock.systemUTC());
    }

    private ValidatedPlugin validated(String id, int order) {
        return validated(new LifecyclePlugin(id, order, false, false));
    }

    private ValidatedPlugin validated(LifecyclePlugin plugin) {
        return validated(plugin, PluginConfig.empty());
    }

    private ValidatedPlugin validated(LifecyclePlugin plugin, PluginConfig config) {
        DiscoveredPlugin discovered = new DiscoveredPlugin(
                plugin,
                PluginSource.BUILT_IN,
                Path.of("built-in", plugin.descriptor().id()));
        return new ValidatedPlugin(discovered, plugin.descriptor(), config, plugin, List.of());
    }

    private static YierLoomConfig config(
            Map<String, PluginConfig> pluginConfigs,
            Map<String, ConfigOrigin> origins
    ) {
        return new YierLoomConfig(
                true,
                Optional.empty(),
                16,
                "test-service",
                pluginConfigs,
                origins,
                List.of());
    }

    private class LifecyclePlugin implements YierLoomPlugin, RuntimeCapability {
        private final PluginDescriptor descriptor;
        private final boolean failAfterScheduling;
        private final boolean failStop;

        private LifecyclePlugin(String id, int order, boolean failAfterScheduling, boolean failStop) {
            descriptor = new PluginDescriptor(id, id, "1.0.0", "1.0.0", true, order);
            this.failAfterScheduling = failAfterScheduling;
            this.failStop = failStop;
        }

        @Override
        public PluginDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public void start(PluginRuntimeContext context) {
            startCalls.add(descriptor.id());
            context.scheduler().scheduleWithFixedDelay(
                    "managed-task", Duration.ofDays(1), Duration.ofDays(1), () -> { });
            if (failAfterScheduling) {
                throw new IllegalStateException("runtime start failed");
            }
        }

        @Override
        public void stop() {
            stopCalls.add(descriptor.id());
            if (failStop) {
                throw new IllegalStateException("runtime stop failed");
            }
        }
    }

    private static final class RecordingInstrumentationController
            implements PluginInstrumentationController {
        private final String failingPluginId;
        private final List<String> removedPluginIds = new ArrayList<>();
        private final AtomicInteger removeAllCalls = new AtomicInteger();
        private boolean failRemoval;
        private int removeAllFailures;

        private RecordingInstrumentationController(String failingPluginId) {
            this.failingPluginId = failingPluginId;
        }

        @Override
        public void install(ValidatedPlugin plugin) {
            if (plugin.descriptor().id().equals(failingPluginId)) {
                throw new IllegalStateException("instrumentation install failed");
            }
        }

        @Override
        public void removePlugin(String pluginId) {
            removedPluginIds.add(pluginId);
            if (failRemoval) {
                throw new IllegalStateException("remove failed");
            }
        }

        @Override
        public void removeAll() {
            removeAllCalls.incrementAndGet();
            if (removeAllFailures > 0) {
                removeAllFailures--;
                throw new IllegalStateException("remove all failed");
            }
        }

        private List<String> removedPluginIds() {
            return removedPluginIds;
        }
    }
}
