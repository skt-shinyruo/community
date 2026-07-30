package com.nowcoder.yierloom.core.plugin;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.nowcoder.yierloom.api.EventSink;
import com.nowcoder.yierloom.api.PluginConfigurationException;
import com.nowcoder.yierloom.core.FatalFailures;
import com.nowcoder.yierloom.core.config.ConfigOrigin;
import com.nowcoder.yierloom.core.config.YierLoomConfig;
import com.nowcoder.yierloom.core.event.YierLoomEventPipeline;
import com.nowcoder.yierloom.core.instrumentation.PluginInstrumentationController;
import com.nowcoder.yierloom.core.runtime.DefaultPluginRuntimeContext;
import com.nowcoder.yierloom.core.runtime.ManagedSchedulerRegistry;

public final class PluginLifecycleManager {
    private static final Comparator<ValidatedPlugin> PLUGIN_ORDER = Comparator
            .comparingInt((ValidatedPlugin plugin) -> plugin.descriptor().order())
            .thenComparing(plugin -> plugin.descriptor().id())
            .thenComparing(plugin -> plugin.discovered().sourcePath().toString());

    private final YierLoomConfig config;
    private final YierLoomEventPipeline pipeline;
    private final ManagedSchedulerRegistry schedulers;
    private final PluginInstrumentationController instrumentation;
    private final List<YierLoomPluginClassLoader> remainingExternalLoaders;
    private final Clock clock;
    private final Map<String, PluginState> states = new HashMap<>();
    private final List<ValidatedPlugin> active = new ArrayList<>();
    private final List<ValidatedPlugin> pendingRuntimeStops = new ArrayList<>();
    private final Map<String, PluginReport> reports = new LinkedHashMap<>();
    private boolean runtimesStopped;
    private boolean tasksCancelled;
    private boolean tasksQuiesced;
    private boolean transformersRemoved;
    private boolean loadersClosed;

    public PluginLifecycleManager(
            YierLoomConfig config,
            YierLoomEventPipeline pipeline,
            ManagedSchedulerRegistry schedulers,
            PluginInstrumentationController instrumentation,
            List<YierLoomPluginClassLoader> externalLoaders,
            Clock clock
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.schedulers = Objects.requireNonNull(schedulers, "schedulers");
        this.instrumentation = Objects.requireNonNull(instrumentation, "instrumentation");
        this.remainingExternalLoaders = new ArrayList<>(
                Objects.requireNonNull(externalLoaders, "externalLoaders"));
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized void activate(List<ValidatedPlugin> plugins) {
        Objects.requireNonNull(plugins, "plugins");
        if (runtimesStopped || tasksCancelled || transformersRemoved || loadersClosed) {
            throw new IllegalStateException("plugin lifecycle is shutting down");
        }
        List<ValidatedPlugin> ordered = new ArrayList<>(plugins);
        ordered.sort(PLUGIN_ORDER);
        for (ValidatedPlugin plugin : ordered) {
            activateOne(Objects.requireNonNull(plugin, "plugin"));
        }
    }

    private void activateOne(ValidatedPlugin plugin) {
        String id = plugin.descriptor().id();
        boolean runtimeAttempted = false;
        try {
            pipeline.registerPlugin(id);
            states.put(id, PluginState.STARTING);
            reports.put(id, PluginReport.lifecycle(plugin, PluginState.STARTING, null, ""));
            if (plugin.hasRuntime()) {
                DefaultPluginRuntimeContext context = contextFor(plugin);
                runtimeAttempted = true;
                plugin.runtimeCapability().start(context);
            }
            instrumentation.install(plugin);
            active.add(plugin);
            states.put(id, PluginState.ACTIVE);
            reports.put(id, PluginReport.lifecycle(plugin, PluginState.ACTIVE, null, ""));
        } catch (VirtualMachineError | ThreadDeath fatal) {
            rollback(plugin, runtimeAttempted);
            reports.put(id, PluginReport.lifecycle(
                    plugin, PluginState.FAILED, "ACTIVATION_FAILED", ""));
            throw fatal;
        } catch (PluginConfigurationException invalidConfig) {
            Throwable fatal = FatalFailures.find(invalidConfig);
            Throwable cleanupFatal = rollback(plugin, runtimeAttempted);
            ConfigOrigin origin = config.originForPluginKey(id, invalidConfig.key());
            reports.put(id, PluginReport.lifecycle(
                    plugin,
                    PluginState.FAILED,
                    "INVALID_CONFIG",
                    PluginReport.configDetail(invalidConfig.key(), origin)));
            throwIfFatal(fatal == null ? cleanupFatal : fatal);
        } catch (Throwable failure) {
            Throwable fatal = fatalCause(failure);
            Throwable cleanupFatal = rollback(plugin, runtimeAttempted);
            reports.put(id, PluginReport.lifecycle(
                    plugin, PluginState.FAILED, "ACTIVATION_FAILED", ""));
            if (fatal != null) {
                throwIfFatal(fatal);
            }
            throwIfFatal(cleanupFatal);
        }
    }

    private DefaultPluginRuntimeContext contextFor(ValidatedPlugin plugin) {
        String id = plugin.descriptor().id();
        EventSink events = pipeline.events(id);
        return new DefaultPluginRuntimeContext(
                plugin.config(),
                schedulers.forPlugin(id, events),
                pipeline.observations(id),
                events,
                System.getLogger("com.nowcoder.yierloom.plugin." + id),
                clock);
    }

    private Throwable rollback(ValidatedPlugin plugin, boolean runtimeAttempted) {
        String id = plugin.descriptor().id();
        FatalFailure fatal = new FatalFailure();
        boolean handlerStopped = true;
        try {
            handlerStopped = pipeline.unregisterPlugin(id);
        } catch (Throwable failure) {
            fatal.record(fatalCause(failure));
            handlerStopped = false;
        }
        compensate(() -> instrumentation.removePlugin(id), fatal);
        boolean pluginTasksStopped;
        try {
            pluginTasksStopped = schedulers.closePlugin(id);
        } catch (Throwable failure) {
            fatal.record(fatalCause(failure));
            pluginTasksStopped = false;
        }
        if (runtimeAttempted && handlerStopped && pluginTasksStopped) {
            compensate(plugin.runtimeCapability()::stop, fatal);
        } else if (runtimeAttempted) {
            pendingRuntimeStops.add(plugin);
        }
        active.removeIf(activePlugin -> activePlugin.descriptor().id().equals(id));
        states.put(id, PluginState.FAILED);
        return fatal.failure;
    }

    public synchronized void stopRuntimesInReverseOrder() {
        if (runtimesStopped) {
            return;
        }
        runtimesStopped = true;
        FatalFailure fatal = new FatalFailure();
        List<ValidatedPlugin> runtimes = new ArrayList<>(active);
        for (ValidatedPlugin pending : pendingRuntimeStops) {
            if (!runtimes.contains(pending)) {
                runtimes.add(pending);
            }
        }
        for (int index = runtimes.size() - 1; index >= 0; index--) {
            ValidatedPlugin plugin = runtimes.get(index);
            String id = plugin.descriptor().id();
            states.put(id, PluginState.STOPPING);
            reports.put(id, PluginReport.lifecycle(plugin, PluginState.STOPPING, null, ""));
            String reason = null;
            if (plugin.hasRuntime()) {
                try {
                    plugin.runtimeCapability().stop();
                } catch (VirtualMachineError | ThreadDeath failure) {
                    fatal.record(failure);
                    reason = "RUNTIME_STOP_FAILED";
                } catch (Throwable failure) {
                    fatal.record(fatalCause(failure));
                    reason = "RUNTIME_STOP_FAILED";
                }
            }
            try {
                pipeline.unregisterPlugin(id);
            } catch (VirtualMachineError | ThreadDeath failure) {
                fatal.record(failure);
            } catch (Throwable failure) {
                fatal.record(fatalCause(failure));
                // Remaining shutdown phases must still run.
            }
            states.put(id, PluginState.STOPPED);
            reports.put(id, PluginReport.lifecycle(plugin, PluginState.STOPPED, reason, ""));
        }
        active.clear();
        pendingRuntimeStops.clear();
        throwIfFatal(fatal.failure);
    }

    public synchronized boolean cancelManagedTasks() {
        if (tasksCancelled) {
            return tasksQuiesced;
        }
        tasksCancelled = true;
        FatalFailure fatal = new FatalFailure();
        compensate(schedulers::rejectNewTasks, fatal);
        try {
            tasksQuiesced = schedulers.closeAndAwait(java.time.Duration.ofSeconds(2));
        } catch (Throwable failure) {
            fatal.record(fatalCause(failure));
            tasksQuiesced = false;
        }
        throwIfFatal(fatal.failure);
        return tasksQuiesced;
    }

    public synchronized boolean removeTransformers() {
        if (transformersRemoved) {
            return pendingRuntimeStops.isEmpty();
        }
        try {
            instrumentation.removeAll();
            transformersRemoved = true;
            return pendingRuntimeStops.isEmpty();
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            rethrowFatal(failure);
            // Retain loaders while a transformer may still reference plugin classes.
            return false;
        }
    }

    public synchronized boolean closeExternalLoaders() {
        if (loadersClosed || !transformersRemoved || !pendingRuntimeStops.isEmpty()) {
            return loadersClosed;
        }
        FatalFailure fatal = new FatalFailure();
        boolean ordinaryFailure = false;
        for (YierLoomPluginClassLoader loader : List.copyOf(remainingExternalLoaders)) {
            try {
                loader.close();
                remainingExternalLoaders.remove(loader);
            } catch (VirtualMachineError | ThreadDeath failure) {
                fatal.record(failure);
            } catch (Throwable failure) {
                fatal.record(fatalCause(failure));
                ordinaryFailure |= fatalCause(failure) == null;
                // Each retained candidate loader is closed independently.
            }
        }
        loadersClosed = remainingExternalLoaders.isEmpty();
        throwIfFatal(fatal.failure);
        if (ordinaryFailure) {
            throw new IllegalStateException("YierLoom plugin loader cleanup failed");
        }
        return loadersClosed;
    }

    public synchronized List<String> activePluginIds() {
        return active.stream().map(plugin -> plugin.descriptor().id()).toList();
    }

    public synchronized List<PluginReport> reports() {
        return List.copyOf(reports.values());
    }

    private static void compensate(Cleanup cleanup, FatalFailure fatal) {
        try {
            cleanup.run();
        } catch (Throwable failure) {
            fatal.record(fatalCause(failure));
            // Compensation steps are intentionally isolated from one another.
        }
    }

    private static void rethrowFatal(Throwable failure) {
        throwIfFatal(fatalCause(failure));
    }

    private static void throwIfFatal(Throwable failure) {
        if (failure instanceof VirtualMachineError fatal) {
            throw fatal;
        }
        if (failure instanceof ThreadDeath fatal) {
            throw fatal;
        }
    }

    private static Throwable fatalCause(Throwable failure) {
        return FatalFailures.find(failure);
    }

    @FunctionalInterface
    private interface Cleanup {
        void run() throws Throwable;
    }

    private static final class FatalFailure {
        private Throwable failure;

        private void record(Throwable candidate) {
            if (failure == null && candidate != null) {
                failure = candidate;
            }
        }
    }
}
