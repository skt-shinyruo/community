package com.nowcoder.yierloom.core;

import java.io.PrintStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.nowcoder.yierloom.api.YierLoomBridge;
import com.nowcoder.yierloom.core.config.YierLoomConfig;
import com.nowcoder.yierloom.core.event.YierLoomEventPipeline;
import com.nowcoder.yierloom.core.instrumentation.ByteBuddyInstrumentationController;
import com.nowcoder.yierloom.core.instrumentation.PluginInstrumentationController;
import com.nowcoder.yierloom.core.plugin.PluginDiscovery;
import com.nowcoder.yierloom.core.plugin.PluginIssue;
import com.nowcoder.yierloom.core.plugin.PluginLifecycleManager;
import com.nowcoder.yierloom.core.plugin.PluginReport;
import com.nowcoder.yierloom.core.plugin.PluginState;
import com.nowcoder.yierloom.core.plugin.PluginValidator;
import com.nowcoder.yierloom.core.plugin.YierLoomPluginClassLoader;
import com.nowcoder.yierloom.core.runtime.ManagedSchedulerRegistry;

public final class EngineRuntime implements AutoCloseable {
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(2);

    private final YierLoomBridge.Endpoint bridgeEndpoint;
    private final CloseActions closeActions;
    private final AtomicReference<EngineState> state = new AtomicReference<>(EngineState.RUNNING);
    private final Object closeMonitor = new Object();
    private boolean bootstrapCleanupPending;

    EngineRuntime(YierLoomBridge.Endpoint bridgeEndpoint, CloseActions closeActions) {
        this.bridgeEndpoint = Objects.requireNonNull(bridgeEndpoint, "bridgeEndpoint");
        this.closeActions = Objects.requireNonNull(closeActions, "closeActions");
    }

    public static EngineRuntime start(
            YierLoomConfig config,
            Instrumentation instrumentation,
            Path workDirectory,
            BootstrapCleanup bootstrapCleanup
    ) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(instrumentation, "instrumentation");
        Objects.requireNonNull(bootstrapCleanup, "bootstrapCleanup");
        Path work = Objects.requireNonNull(workDirectory, "workDirectory")
                .toAbsolutePath()
                .normalize();
        Path injectionDirectory = work.resolve("injection").normalize();
        if (!injectionDirectory.startsWith(work)) {
            throw new EngineInitializationException();
        }

        YierLoomEventPipeline pipeline = null;
        ManagedSchedulerRegistry schedulers = null;
        PluginInstrumentationController instrumentationController = null;
        PluginDiscovery.Result discovery = null;
        EngineRuntime runtime = null;
        boolean bridgeInstalled = false;
        try {
            Clock clock = Clock.systemUTC();
            pipeline = new YierLoomEventPipeline(config, clock, System.out);
            pipeline.start();
            if (!YierLoomBridge.install(pipeline)) {
                throw new IllegalStateException("YierLoom Bridge endpoint is already installed");
            }
            bridgeInstalled = true;

            schedulers = new ManagedSchedulerRegistry();
            instrumentationController = new ByteBuddyInstrumentationController(
                    instrumentation, injectionDirectory, clock);
            discovery = new PluginDiscovery().discover(config, YierLoomEngine.class.getClassLoader());
            PluginLifecycleManager lifecycle = new PluginLifecycleManager(
                    config,
                    pipeline,
                    schedulers,
                    instrumentationController,
                    discovery.externalLoaders(),
                    clock);
            runtime = new EngineRuntime(
                    pipeline,
                    new DefaultCloseActions(
                            pipeline, schedulers, lifecycle, bootstrapCleanup));

            PluginValidator.ValidationResult validation = new PluginValidator()
                    .validate(discovery.plugins(), config);
            lifecycle.activate(validation.plugins());
            printStatus(config, discovery, validation, lifecycle, System.err);
            return runtime;
        } catch (Throwable failure) {
            Throwable cleanupFailure;
            if (runtime != null) {
                cleanupFailure = closeAndCapture(runtime);
            } else {
                cleanupFailure = rollbackPartial(
                        pipeline,
                        schedulers,
                        instrumentationController,
                        discovery,
                        bridgeInstalled,
                        bootstrapCleanup);
            }
            Throwable fatal = FatalFailures.find(failure);
            if (fatal == null) {
                fatal = FatalFailures.find(cleanupFailure);
            }
            FatalFailures.rethrow(fatal);
            throw new EngineInitializationException();
        }
    }

    @Override
    public void close() {
        synchronized (closeMonitor) {
            if (state.get() == EngineState.STOPPED && bootstrapCleanupPending) {
                retryBootstrapCleanup();
                return;
            }
            if (!state.compareAndSet(EngineState.RUNNING, EngineState.STOPPING)) {
                return;
            }
            CleanupFailures failures = new CleanupFailures();
            ResourceRelease resources = new ResourceRelease();
            runCloseStep(closeActions::rejectNewTasks, failures);
            runCloseStep(() -> resources.handlersStopped = closeActions.stopObservations(), failures);
            runCloseStep(() -> resources.tasksStopped = closeActions.cancelManagedTasks(), failures);
            if (resources.handlersStopped && resources.tasksStopped) {
                runCloseStep(closeActions::stopPluginRuntimes, failures);
            }
            runCloseStep(() -> resources.transformersReleased = closeActions.removeTransformers(), failures);
            runCloseStep(() -> closeActions.clearBridge(bridgeEndpoint), failures);
            runCloseStep(() -> resources.eventWorkerStopped = closeActions.drainEvents(DRAIN_TIMEOUT), failures);
            boolean retainResources = !resources.releasable();
            if (resources.releasable()) {
                runCloseStep(
                        () -> resources.loadersClosed = closeActions.closePluginLoaders(),
                        failures);
                if (!resources.loadersClosed) {
                    runCloseStep(
                            () -> resources.loadersClosed = closeActions.closePluginLoaders(),
                            failures);
                }
                retainResources = !resources.loadersClosed;
                if (!retainResources) {
                    bootstrapCleanupPending = !runCloseStep(
                            closeActions::cleanupBootstrap, failures);
                    if (bootstrapCleanupPending) {
                        bootstrapCleanupPending = !runCloseStep(
                                closeActions::cleanupBootstrap, failures);
                    }
                }
            }
            if (retainResources) {
                runCloseStep(closeActions::retainBootstrap, failures);
            }
            state.set(EngineState.STOPPED);
            if (retainResources) {
                runCloseStep(() -> System.err.println(
                        "[YierLoom] retained agent resources after incomplete shutdown"), failures);
            }
            if (failures.ordinaryCount > 0) {
                int ordinaryFailures = failures.ordinaryCount;
                runCloseStep(() -> System.err.println(
                        "[YierLoom] shutdown cleanup failures: " + ordinaryFailures), failures);
            }
            FatalFailures.rethrow(failures.fatal);
        }
    }

    private void retryBootstrapCleanup() {
        CleanupFailures failures = new CleanupFailures();
        bootstrapCleanupPending = !runCloseStep(closeActions::cleanupBootstrap, failures);
        if (failures.ordinaryCount > 0) {
            int ordinaryFailures = failures.ordinaryCount;
            runCloseStep(() -> System.err.println(
                    "[YierLoom] shutdown cleanup failures: " + ordinaryFailures), failures);
        }
        FatalFailures.rethrow(failures.fatal);
    }

    public EngineState state() {
        return state.get();
    }

    interface CloseActions {
        void rejectNewTasks();

        boolean stopObservations();

        void stopPluginRuntimes();

        boolean cancelManagedTasks();

        boolean removeTransformers();

        void retainBootstrap();

        void clearBridge(YierLoomBridge.Endpoint endpoint);

        boolean drainEvents(Duration timeout);

        boolean closePluginLoaders();

        void cleanupBootstrap();
    }

    private static void printStatus(
            YierLoomConfig config,
            PluginDiscovery.Result discovery,
            PluginValidator.ValidationResult validation,
            PluginLifecycleManager lifecycle,
            PrintStream output
    ) {
        statusLines(
                config,
                discovery,
                validation,
                lifecycle.reports(),
                lifecycle.activePluginIds().size())
                .forEach(output::println);
    }

    static List<String> statusLines(
            YierLoomConfig config,
            PluginDiscovery.Result discovery,
            PluginValidator.ValidationResult validation,
            List<PluginReport> lifecycleReports,
            int activeCount
    ) {
        Map<PluginIdentity, PluginReport> lifecycleOutcomes = new LinkedHashMap<>();
        lifecycleReports.forEach(report -> lifecycleOutcomes.put(identity(report), report));
        Set<PluginIdentity> mergedLifecycle = new LinkedHashSet<>();
        List<PluginReport> outcomes = new ArrayList<>();
        for (PluginReport report : validation.reports()) {
            PluginIdentity plugin = identity(report);
            PluginReport lifecycle = lifecycleOutcomes.get(plugin);
            if (lifecycle != null && mergedLifecycle.add(plugin)) {
                outcomes.add(lifecycle);
            } else {
                outcomes.add(report);
            }
        }
        lifecycleOutcomes.forEach((plugin, report) -> {
            if (mergedLifecycle.add(plugin)) {
                outcomes.add(report);
            }
        });

        long disabledCount = outcomes.stream()
                .filter(report -> !report.enabled() && report.state() != PluginState.FAILED)
                .count();
        long failedCount = outcomes.stream()
                .filter(report -> report.state() == PluginState.FAILED)
                .count();

        List<String> lines = new ArrayList<>();
        lines.add("[YierLoom] started: discovered=" + discovery.plugins().size()
                + ", enabled=" + validation.plugins().size()
                + ", disabled=" + disabledCount
                + ", active=" + activeCount
                + ", failed=" + failedCount
                + ", discoveryIssues=" + discovery.issues().size()
                + ", configWarnings=" + config.warnings().size());
        for (PluginIssue issue : discovery.issues()) {
            lines.add("[YierLoom] discovery issue: " + issue.summary());
        }
        for (PluginReport report : outcomes) {
            lines.add("[YierLoom] plugin status: " + report.summary());
        }
        for (String warning : config.warnings()) {
            lines.add("[YierLoom] config warning: " + warning);
        }
        return List.copyOf(lines);
    }

    static List<String> shutdownIssueLines(List<PluginReport> reports) {
        return Objects.requireNonNull(reports, "reports").stream()
                .filter(report -> report.state() == PluginState.STOPPED)
                .filter(report -> report.reasonCode() != null)
                .map(report -> "[YierLoom] plugin shutdown issue: " + report.summary())
                .toList();
    }

    private static PluginIdentity identity(PluginReport report) {
        return new PluginIdentity(report.sourcePath(), report.pluginId());
    }

    private static Throwable rollbackPartial(
            YierLoomEventPipeline pipeline,
            ManagedSchedulerRegistry schedulers,
            PluginInstrumentationController instrumentation,
            PluginDiscovery.Result discovery,
            boolean bridgeInstalled,
            BootstrapCleanup bootstrapCleanup
    ) {
        CleanupFailures failures = new CleanupFailures();
        ResourceRelease resources = new ResourceRelease();
        runCloseStep(() -> {
            if (schedulers != null) {
                schedulers.rejectNewTasks();
            }
        }, failures);
        runCloseStep(() -> {
            resources.handlersStopped = pipeline == null || pipeline.stopObservations();
        }, failures);
        runCloseStep(() -> {
            resources.tasksStopped = schedulers == null
                    || schedulers.closeAndAwait(DRAIN_TIMEOUT);
        }, failures);
        runCloseStep(() -> {
            if (instrumentation != null) {
                instrumentation.removeAll();
            }
            resources.transformersReleased = true;
        }, failures);
        runCloseStep(() -> {
            if (bridgeInstalled && pipeline != null) {
                YierLoomBridge.clear(pipeline);
            }
        }, failures);
        runCloseStep(() -> {
            resources.eventWorkerStopped = pipeline == null
                    || pipeline.drainAndClose(DRAIN_TIMEOUT);
        }, failures);
        if (resources.releasable()) {
            runCloseStep(
                    () -> resources.loadersClosed = closeExternalLoaders(discovery),
                    failures);
            if (!resources.loadersClosed) {
                runCloseStep(
                        () -> resources.loadersClosed = closeExternalLoaders(discovery),
                        failures);
            }
        }
        if (resources.releasableWithLoaders()) {
            runCloseStep(bootstrapCleanup::run, failures);
        } else {
            runCloseStep(bootstrapCleanup::retain, failures);
        }
        return failures.fatal;
    }

    private static boolean closeExternalLoaders(PluginDiscovery.Result discovery) {
        if (discovery == null) {
            return true;
        }
        Throwable first = null;
        boolean ordinaryFailure = false;
        for (YierLoomPluginClassLoader loader : discovery.externalLoaders()) {
            try {
                loader.close();
            } catch (VirtualMachineError | ThreadDeath fatal) {
                if (first == null) {
                    first = fatal;
                }
            } catch (Throwable failure) {
                Throwable fatal = FatalFailures.find(failure);
                if (first == null && fatal != null) {
                    first = fatal;
                }
                ordinaryFailure |= fatal == null;
            }
        }
        FatalFailures.rethrow(first);
        if (ordinaryFailure) {
            throw new IllegalStateException("YierLoom plugin loader cleanup failed");
        }
        return true;
    }

    private static Throwable closeAndCapture(EngineRuntime runtime) {
        try {
            runtime.close();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static boolean runCloseStep(CloseStep step, CleanupFailures failures) {
        try {
            step.run();
            return true;
        } catch (Throwable failure) {
            Throwable fatal = FatalFailures.find(failure);
            if (fatal == null) {
                failures.ordinaryCount++;
            } else if (failures.fatal == null) {
                failures.fatal = fatal;
            }
            return false;
        }
    }

    static Throwable fatalCause(Throwable failure) {
        return FatalFailures.find(failure);
    }

    static void rethrowFatal(Throwable failure) {
        FatalFailures.rethrow(failure);
    }

    @FunctionalInterface
    private interface CloseStep {
        void run() throws Throwable;
    }

    private static final class CleanupFailures {
        private Throwable fatal;
        private int ordinaryCount;
    }

    private static final class ResourceRelease {
        private boolean handlersStopped;
        private boolean tasksStopped;
        private boolean transformersReleased;
        private boolean eventWorkerStopped;
        private boolean loadersClosed;

        private boolean releasable() {
            return handlersStopped && tasksStopped && transformersReleased && eventWorkerStopped;
        }

        private boolean releasableWithLoaders() {
            return releasable() && loadersClosed;
        }
    }

    private record PluginIdentity(Path sourcePath, String pluginId) {
    }

    private record DefaultCloseActions(
            YierLoomEventPipeline pipeline,
            ManagedSchedulerRegistry schedulers,
            PluginLifecycleManager lifecycle,
            BootstrapCleanup bootstrapCleanup
    ) implements CloseActions {
        private DefaultCloseActions {
            Objects.requireNonNull(pipeline, "pipeline");
            Objects.requireNonNull(schedulers, "schedulers");
            Objects.requireNonNull(lifecycle, "lifecycle");
            Objects.requireNonNull(bootstrapCleanup, "bootstrapCleanup");
        }

        @Override
        public void rejectNewTasks() {
            schedulers.rejectNewTasks();
        }

        @Override
        public boolean stopObservations() {
            return pipeline.stopObservations();
        }

        @Override
        public void stopPluginRuntimes() {
            lifecycle.stopRuntimesInReverseOrder();
            shutdownIssueLines(lifecycle.reports()).forEach(System.err::println);
        }

        @Override
        public boolean cancelManagedTasks() {
            return lifecycle.cancelManagedTasks();
        }

        @Override
        public boolean removeTransformers() {
            return lifecycle.removeTransformers();
        }

        @Override
        public void retainBootstrap() {
            bootstrapCleanup.retain();
        }

        @Override
        public void clearBridge(YierLoomBridge.Endpoint endpoint) {
            YierLoomBridge.clear(endpoint);
        }

        @Override
        public boolean drainEvents(Duration timeout) {
            return pipeline.drainAndClose(timeout);
        }

        @Override
        public boolean closePluginLoaders() {
            return lifecycle.closeExternalLoaders();
        }

        @Override
        public void cleanupBootstrap() {
            bootstrapCleanup.run();
        }
    }
}
