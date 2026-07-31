package com.nowcoder.yierloom.core;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.nowcoder.yierloom.api.DiagnosticEvent;
import com.nowcoder.yierloom.api.PluginObservation;
import com.nowcoder.yierloom.api.YierLoomBridge;
import com.nowcoder.yierloom.core.config.ConfigSource;
import com.nowcoder.yierloom.core.config.YierLoomConfig;
import com.nowcoder.yierloom.core.plugin.PluginDiscovery;
import com.nowcoder.yierloom.core.plugin.PluginIssue;
import com.nowcoder.yierloom.core.plugin.PluginReport;
import com.nowcoder.yierloom.core.plugin.PluginState;
import com.nowcoder.yierloom.core.plugin.PluginValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YierLoomEngineTest {
    private final List<YierLoomBridge.Endpoint> endpoints = new ArrayList<>();

    @TempDir
    java.nio.file.Path tempDir;

    @AfterEach
    void clearEndpoints() {
        endpoints.forEach(YierLoomBridge::clear);
    }

    @Test
    void disabledAgentCreatesNoPipelineTaskOrTransformer() {
        AtomicInteger instrumentationCalls = new AtomicInteger();
        Instrumentation instrumentation = instrumentationCalls(instrumentationCalls);
        AtomicInteger cleanupCalls = new AtomicInteger();
        Set<String> before = yierloomThreadNames();

        YierLoomEngine.start(
                "yierloom.enabled=false",
                instrumentation,
                tempDir,
                cleanupCalls::incrementAndGet);

        assertThat(instrumentationCalls).hasValue(0);
        assertThat(cleanupCalls).hasValue(1);
        assertThat(yierloomThreadNames()).isEqualTo(before);
        assertThat(YierLoomBridge.emit(
                "method", DiagnosticEvent.builder("should-drop").build())).isFalse();
    }

    @Test
    void closesInTheSpecifiedOrderAndClearsOnlyItsEndpoint() {
        List<String> calls = new ArrayList<>();
        YierLoomBridge.Endpoint endpoint = endpoint();
        assertThat(YierLoomBridge.install(endpoint)).isTrue();
        RecordingCloseActions actions = new RecordingCloseActions(calls);
        EngineRuntime runtime = new EngineRuntime(endpoint, actions);

        runtime.close();
        runtime.close();

        assertThat(calls).containsExactly(
                "scheduler-reject",
                "observations-stop",
                "tasks-cancel",
                "plugins-stop-reverse",
                "transformers-remove",
                "bridge-clear",
                "events-drain-2s",
                "plugin-loaders-close",
                "bootstrap-cleanup");
        assertThat(runtime.state()).isEqualTo(EngineState.STOPPED);
        assertThat(YierLoomBridge.emit(
                "method", DiagnosticEvent.builder("after-close").build())).isFalse();
    }

    @Test
    void ordinaryCleanupFailureDoesNotBlockLaterPhases() {
        List<String> calls = new ArrayList<>();
        YierLoomBridge.Endpoint endpoint = endpoint();
        assertThat(YierLoomBridge.install(endpoint)).isTrue();
        RecordingCloseActions actions = new RecordingCloseActions(calls);
        actions.failureAt = "plugins-stop-reverse";
        EngineRuntime runtime = new EngineRuntime(endpoint, actions);

        runtime.close();

        assertThat(calls).containsExactly(
                "scheduler-reject",
                "observations-stop",
                "tasks-cancel",
                "plugins-stop-reverse",
                "transformers-remove",
                "bridge-clear",
                "events-drain-2s",
                "plugin-loaders-close",
                "bootstrap-cleanup");
        assertThat(runtime.state()).isEqualTo(EngineState.STOPPED);
    }

    @Test
    void deeplyWrappedFatalCleanupFailureIsRethrownAfterEveryPhase() {
        List<String> calls = new ArrayList<>();
        YierLoomBridge.Endpoint endpoint = endpoint();
        assertThat(YierLoomBridge.install(endpoint)).isTrue();
        RecordingCloseActions actions = new RecordingCloseActions(calls);
        Throwable failure = new OutOfMemoryError("fatal");
        for (int depth = 0; depth < 100; depth++) {
            failure = new IllegalStateException("wrapped", failure);
        }
        actions.failureAt = "scheduler-reject";
        actions.failure = failure;
        EngineRuntime runtime = new EngineRuntime(endpoint, actions);

        assertThatThrownBy(runtime::close).isInstanceOf(OutOfMemoryError.class);

        assertThat(calls).containsExactly(
                "scheduler-reject",
                "observations-stop",
                "tasks-cancel",
                "plugins-stop-reverse",
                "transformers-remove",
                "bridge-clear",
                "events-drain-2s",
                "plugin-loaders-close",
                "bootstrap-cleanup");
        assertThat(runtime.state()).isEqualTo(EngineState.STOPPED);
    }

    @Test
    void closeDoesNotClearAReplacementBridgeEndpoint() {
        List<String> calls = new ArrayList<>();
        YierLoomBridge.Endpoint owned = endpoint();
        YierLoomBridge.Endpoint replacement = endpoint();
        assertThat(YierLoomBridge.install(owned)).isTrue();
        EngineRuntime runtime = new EngineRuntime(owned, new RecordingCloseActions(calls));
        assertThat(YierLoomBridge.clear(owned)).isTrue();
        assertThat(YierLoomBridge.install(replacement)).isTrue();

        runtime.close();

        assertThat(YierLoomBridge.emit(
                "method", DiagnosticEvent.builder("replacement").build())).isTrue();
    }

    @Test
    void retainsBootstrapResourcesWhenTransformerRemovalIsIncomplete() {
        List<String> calls = new ArrayList<>();
        YierLoomBridge.Endpoint endpoint = endpoint();
        assertThat(YierLoomBridge.install(endpoint)).isTrue();
        RecordingCloseActions actions = new RecordingCloseActions(calls);
        actions.transformersReleased = false;
        EngineRuntime runtime = new EngineRuntime(endpoint, actions);

        runtime.close();

        assertThat(calls).containsExactly(
                "scheduler-reject",
                "observations-stop",
                "tasks-cancel",
                "plugins-stop-reverse",
                "transformers-remove",
                "bridge-clear",
                "events-drain-2s",
                "bootstrap-retain");
        assertThat(runtime.state()).isEqualTo(EngineState.STOPPED);
    }

    @Test
    void retainedBootstrapCleanupCannotBeInvokedByAnOuterStartupCatch() {
        AtomicInteger cleanupCalls = new AtomicInteger();
        BootstrapCleanup cleanup = new BootstrapCleanup(cleanupCalls::incrementAndGet);

        cleanup.retain();
        cleanup.run();
        cleanup.run();

        assertThat(cleanupCalls).hasValue(0);
        assertThat(cleanup.retained()).isTrue();
    }

    @Test
    void ordinaryBootstrapCleanupFailureCanBeRetried() {
        AtomicInteger cleanupCalls = new AtomicInteger();
        BootstrapCleanup cleanup = new BootstrapCleanup(() -> {
            if (cleanupCalls.incrementAndGet() == 1) {
                throw new IllegalStateException("first cleanup failed");
            }
        });

        assertThatThrownBy(cleanup::run).isInstanceOf(IllegalStateException.class);
        cleanup.run();
        cleanup.run();

        assertThat(cleanupCalls).hasValue(2);
    }

    @Test
    void cleanupFailureIsNotMaskedWhenOwnershipReleaseAlsoFails() {
        IllegalStateException cleanupFailure = new IllegalStateException("cleanup");
        SecurityException ownershipFailure = new SecurityException("ownership");
        OwnershipAwareCleanup delegate = new OwnershipAwareCleanup(
                cleanupFailure, ownershipFailure);
        BootstrapCleanup cleanup = new BootstrapCleanup(delegate);

        cleanup.acceptOwnership();

        assertThatThrownBy(cleanup::run)
                .isInstanceOfSatisfying(BootstrapCleanup.CombinedFailure.class, combined -> {
                    assertThat(combined.getCause()).isSameAs(cleanupFailure);
                    assertThat(combined.ownershipFailure()).isSameAs(ownershipFailure);
                    assertThat(combined.getSuppressed()).isEmpty();
                });
        assertThat(delegate.ownershipAccepted).isFalse();
    }

    @Test
    void fatalOwnershipReleaseFailureWinsOverOrdinaryCleanupFailure() {
        ThreadDeath fatal = new ThreadDeath();
        OwnershipAwareCleanup delegate = new OwnershipAwareCleanup(
                new IllegalStateException("cleanup"), fatal);
        BootstrapCleanup cleanup = new BootstrapCleanup(delegate);

        cleanup.acceptOwnership();

        assertThatThrownBy(cleanup::run).isSameAs(fatal);
    }

    @Test
    void engineRetriesCleanupAfterAnEarlyConfigurationFailure() {
        AtomicInteger cleanupCalls = new AtomicInteger();

        YierLoomEngine.start(
                "yierloom.event-queue-capacity=invalid",
                instrumentationCalls(new AtomicInteger()),
                tempDir,
                () -> {
                    if (cleanupCalls.incrementAndGet() == 1) {
                        throw new IllegalStateException("transient cleanup failure");
                    }
                });

        assertThat(cleanupCalls).hasValue(2);
    }

    @Test
    void runtimeRetriesBootstrapCleanupAndKeepsItRetryableAfterStopping() {
        List<String> calls = new ArrayList<>();
        YierLoomBridge.Endpoint endpoint = endpoint();
        assertThat(YierLoomBridge.install(endpoint)).isTrue();
        RecordingCloseActions actions = new RecordingCloseActions(calls);
        actions.failureAt = "bootstrap-cleanup";
        actions.failureLimit = 2;
        EngineRuntime runtime = new EngineRuntime(endpoint, actions);

        runtime.close();
        runtime.close();
        runtime.close();

        assertThat(calls).filteredOn("bootstrap-cleanup"::equals).hasSize(3);
        assertThat(runtime.state()).isEqualTo(EngineState.STOPPED);
    }

    @Test
    void retainsResourcesAndSkipsRuntimeStopWhenManagedTasksDoNotQuiesce() {
        List<String> calls = new ArrayList<>();
        YierLoomBridge.Endpoint endpoint = endpoint();
        assertThat(YierLoomBridge.install(endpoint)).isTrue();
        RecordingCloseActions actions = new RecordingCloseActions(calls);
        actions.tasksStopped = false;
        EngineRuntime runtime = new EngineRuntime(endpoint, actions);

        runtime.close();

        assertThat(calls).containsExactly(
                "scheduler-reject",
                "observations-stop",
                "tasks-cancel",
                "transformers-remove",
                "bridge-clear",
                "events-drain-2s",
                "bootstrap-retain");
        assertThat(runtime.state()).isEqualTo(EngineState.STOPPED);
    }

    @Test
    void statusSummaryIncludesPluginOutcomesAndReasonsWithoutConfigurationValues() {
        Path builtIn = Path.of("built-in");
        Path external = Path.of("plugins", "broken.jar");
        YierLoomConfig config = new YierLoomConfig(
                true,
                Optional.empty(),
                16,
                "test-service",
                Map.of("broken", com.nowcoder.yierloom.api.PluginConfig.of(
                        Map.of("token", "private-token"))),
                Map.of(),
                List.of("unknown YierLoom configuration key 'yierloom.unknown'"));
        PluginDiscovery.Result discovery = new PluginDiscovery.Result(
                List.of(),
                List.of(new PluginIssue(external, "CANDIDATE_INVALID")),
                List.of());
        PluginValidator.ValidationResult validation = new PluginValidator.ValidationResult(
                List.of(),
                List.of(
                        new PluginReport(builtIn, "active", true, PluginState.VALIDATED, null, ""),
                        new PluginReport(builtIn, "disabled", false, PluginState.VALIDATED, null, ""),
                        new PluginReport(
                                external,
                                "broken",
                                true,
                                PluginState.FAILED,
                                "INVALID_CONFIG",
                                "key=token, origin=" + ConfigSource.SYSTEM_PROPERTY
                                        + "@yierloom.plugins.broken.token")));
        List<PluginReport> lifecycleReports = List.of(
                new PluginReport(builtIn, "active", true, PluginState.ACTIVE, null, ""));

        List<String> lines = EngineRuntime.statusLines(
                config, discovery, validation, lifecycleReports, 1);

        assertThat(lines).containsExactly(
                "[YierLoom] started: discovered=0, enabled=0, disabled=1, active=1, failed=1, "
                        + "discoveryIssues=1, configWarnings=1",
                "[YierLoom] discovery issue: source=plugins/broken.jar, reason=CANDIDATE_INVALID",
                "[YierLoom] plugin status: source=built-in, plugin=active, enabled=true, state=ACTIVE",
                "[YierLoom] plugin status: source=built-in, plugin=disabled, enabled=false, state=VALIDATED",
                "[YierLoom] plugin status: source=plugins/broken.jar, plugin=broken, enabled=true, "
                        + "state=FAILED, reason=INVALID_CONFIG, key=token, "
                        + "origin=SYSTEM_PROPERTY@yierloom.plugins.broken.token",
                "[YierLoom] config warning: unknown YierLoom configuration key 'yierloom.unknown'");
        assertThat(String.join("\n", lines)).doesNotContain("private-token");
    }

    @Test
    void statusSummaryPreservesEveryDuplicatePluginFailure() {
        PluginReport duplicate = new PluginReport(
                Path.of("built-ins.jar"),
                "duplicate",
                false,
                PluginState.FAILED,
                "DUPLICATE_ID",
                "");
        YierLoomConfig config = new YierLoomConfig(
                true, Optional.empty(), 16, "test-service", Map.of(), Map.of(), List.of());
        PluginDiscovery.Result discovery = new PluginDiscovery.Result(List.of(), List.of(), List.of());
        PluginValidator.ValidationResult validation = new PluginValidator.ValidationResult(
                List.of(), List.of(duplicate, duplicate));

        List<String> lines = EngineRuntime.statusLines(
                config, discovery, validation, List.of(), 0);

        assertThat(lines.get(0)).contains("failed=2");
        assertThat(lines).filteredOn(line -> line.contains("plugin=duplicate"))
                .hasSize(2)
                .allMatch(line -> line.contains("reason=DUPLICATE_ID"));
    }

    @Test
    void shutdownIssuesExposeOnlyStablePluginMetadata() {
        List<String> lines = EngineRuntime.shutdownIssueLines(List.of(
                new PluginReport(
                        Path.of("plugins", "stop-failure.jar"),
                        "stop-failure",
                        true,
                        PluginState.STOPPED,
                        "RUNTIME_STOP_FAILED",
                        "exception.message=secret, stack=private"),
                new PluginReport(
                        Path.of("plugins", "healthy.jar"),
                        "healthy",
                        true,
                        PluginState.STOPPED,
                        null,
                        "")));

        assertThat(lines).containsExactly(
                "[YierLoom] plugin shutdown issue: source=plugins/stop-failure.jar, "
                        + "plugin=stop-failure, enabled=true, state=STOPPED, "
                        + "reason=RUNTIME_STOP_FAILED");
        assertThat(String.join("\n", lines))
                .doesNotContain("exception", "message", "secret", "stack", "private");
    }

    private YierLoomBridge.Endpoint endpoint() {
        YierLoomBridge.Endpoint endpoint = new YierLoomBridge.Endpoint() {
            @Override
            public boolean observe(String pluginId, PluginObservation observation) {
                return true;
            }

            @Override
            public boolean emit(String pluginId, DiagnosticEvent event) {
                return true;
            }
        };
        endpoints.add(endpoint);
        return endpoint;
    }

    private static Instrumentation instrumentationCalls(AtomicInteger calls) {
        return (Instrumentation) Proxy.newProxyInstance(
                YierLoomEngineTest.class.getClassLoader(),
                new Class<?>[]{Instrumentation.class},
                (proxy, method, arguments) -> {
                    calls.incrementAndGet();
                    Class<?> returnType = method.getReturnType();
                    if (!returnType.isPrimitive()) {
                        return null;
                    }
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == char.class) {
                        return '\0';
                    }
                    return 0;
                });
    }

    private static Set<String> yierloomThreadNames() {
        return Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName)
                .filter(name -> name.startsWith("yierloom-"))
                .collect(Collectors.toSet());
    }

    public static final class OwnershipAwareCleanup implements Runnable {
        private final Throwable cleanupFailure;
        private final Throwable ownershipFailure;
        private boolean ownershipAccepted;

        private OwnershipAwareCleanup(Throwable cleanupFailure, Throwable ownershipFailure) {
            this.cleanupFailure = cleanupFailure;
            this.ownershipFailure = ownershipFailure;
        }

        public void acceptOwnership() {
            ownershipAccepted = true;
        }

        public void releaseOwnership() {
            ownershipAccepted = false;
            throwFailure(ownershipFailure);
        }

        @Override
        public void run() {
            throwFailure(cleanupFailure);
        }

        private static void throwFailure(Throwable failure) {
            if (failure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            throw new AssertionError(failure);
        }
    }

    private static final class RecordingCloseActions implements EngineRuntime.CloseActions {
        private final List<String> calls;
        private String failureAt;
        private Throwable failure = new IllegalStateException("cleanup value must not escape");
        private int failureLimit = Integer.MAX_VALUE;
        private int failureCalls;
        private boolean transformersReleased = true;
        private boolean tasksStopped = true;

        private RecordingCloseActions(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void rejectNewTasks() {
            record("scheduler-reject");
        }

        @Override
        public boolean stopObservations() {
            record("observations-stop");
            return true;
        }

        @Override
        public void stopPluginRuntimes() {
            record("plugins-stop-reverse");
        }

        @Override
        public boolean cancelManagedTasks() {
            record("tasks-cancel");
            return tasksStopped;
        }

        @Override
        public boolean removeTransformers() {
            record("transformers-remove");
            return transformersReleased;
        }

        @Override
        public void retainBootstrap() {
            record("bootstrap-retain");
        }

        @Override
        public void clearBridge(YierLoomBridge.Endpoint endpoint) {
            record("bridge-clear");
            YierLoomBridge.clear(endpoint);
        }

        @Override
        public boolean drainEvents(Duration timeout) {
            assertThat(timeout).isEqualTo(Duration.ofSeconds(2));
            record("events-drain-2s");
            return true;
        }

        @Override
        public boolean closePluginLoaders() {
            record("plugin-loaders-close");
            return true;
        }

        @Override
        public void cleanupBootstrap() {
            record("bootstrap-cleanup");
        }

        private void record(String call) {
            calls.add(call);
            if (call.equals(failureAt) && failureCalls++ < failureLimit) {
                if (failure instanceof RuntimeException runtimeFailure) {
                    throw runtimeFailure;
                }
                if (failure instanceof Error error) {
                    throw error;
                }
            }
        }
    }
}
