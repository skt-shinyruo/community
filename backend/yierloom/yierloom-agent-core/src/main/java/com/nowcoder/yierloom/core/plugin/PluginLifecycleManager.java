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
    private final List<YierLoomPluginClassLoader> externalLoaders;
    private final Clock clock;
    private final Map<String, PluginState> states = new HashMap<>();
    private final List<ValidatedPlugin> active = new ArrayList<>();
    private final Map<String, PluginReport> reports = new LinkedHashMap<>();
    private boolean runtimesStopped;
    private boolean tasksCancelled;
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
        this.externalLoaders = List.copyOf(
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
                runtimeAttempted = true;
                plugin.runtimeCapability().start(contextFor(plugin));
            }
            instrumentation.install(plugin);
            active.add(plugin);
            states.put(id, PluginState.ACTIVE);
            reports.put(id, PluginReport.lifecycle(plugin, PluginState.ACTIVE, null, ""));
        } catch (VirtualMachineError | ThreadDeath fatal) {
            Throwable cleanupFatal = rollback(plugin, runtimeAttempted);
            reports.put(id, PluginReport.lifecycle(
                    plugin, PluginState.FAILED, "ACTIVATION_FAILED", ""));
            if (cleanupFatal != null) {
                fatal.addSuppressed(cleanupFatal);
            }
            throw fatal;
        } catch (PluginConfigurationException invalidConfig) {
            Throwable cleanupFatal = rollback(plugin, runtimeAttempted);
            throwIfFatal(cleanupFatal);
            ConfigOrigin origin = config.originForPluginKey(id, invalidConfig.key());
            reports.put(id, PluginReport.lifecycle(
                    plugin,
                    PluginState.FAILED,
                    "INVALID_CONFIG",
                    PluginReport.configDetail(invalidConfig.key(), origin)));
        } catch (Throwable failure) {
            rethrowFatal(failure);
            Throwable cleanupFatal = rollback(plugin, runtimeAttempted);
            throwIfFatal(cleanupFatal);
            reports.put(id, PluginReport.lifecycle(
                    plugin, PluginState.FAILED, "ACTIVATION_FAILED", ""));
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
        compensate(() -> instrumentation.removePlugin(id), fatal);
        if (runtimeAttempted) {
            compensate(plugin.runtimeCapability()::stop, fatal);
        }
        compensate(() -> schedulers.closePlugin(id), fatal);
        compensate(() -> pipeline.unregisterPlugin(id), fatal);
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
        for (int index = active.size() - 1; index >= 0; index--) {
            ValidatedPlugin plugin = active.get(index);
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
                    reason = "RUNTIME_STOP_FAILED";
                }
            }
            try {
                pipeline.unregisterPlugin(id);
            } catch (VirtualMachineError | ThreadDeath failure) {
                fatal.record(failure);
            } catch (Throwable ignored) {
                // Remaining shutdown phases must still run.
            }
            states.put(id, PluginState.STOPPED);
            reports.put(id, PluginReport.lifecycle(plugin, PluginState.STOPPED, reason, ""));
        }
        active.clear();
        throwIfFatal(fatal.failure);
    }

    public synchronized void cancelManagedTasks() {
        if (tasksCancelled) {
            return;
        }
        tasksCancelled = true;
        try {
            schedulers.rejectNewTasks();
            schedulers.close();
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable ignored) {
            // A scheduler failure must not block transformer removal.
        }
    }

    public synchronized void removeTransformers() {
        if (transformersRemoved) {
            return;
        }
        try {
            instrumentation.removeAll();
            transformersRemoved = true;
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable ignored) {
            // Retain loaders while a transformer may still reference plugin classes.
        }
    }

    public synchronized void closeExternalLoaders() {
        if (loadersClosed || !transformersRemoved) {
            return;
        }
        loadersClosed = true;
        FatalFailure fatal = new FatalFailure();
        for (YierLoomPluginClassLoader loader : externalLoaders) {
            try {
                loader.close();
            } catch (VirtualMachineError | ThreadDeath failure) {
                fatal.record(failure);
            } catch (Throwable ignored) {
                // Each retained candidate loader is closed independently.
            }
        }
        throwIfFatal(fatal.failure);
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
        } catch (VirtualMachineError | ThreadDeath failure) {
            fatal.record(failure);
        } catch (Throwable ignored) {
            // Compensation steps are intentionally isolated from one another.
        }
    }

    private static void rethrowFatal(Throwable failure) {
        if (failure instanceof VirtualMachineError fatal) {
            throw fatal;
        }
        if (failure instanceof ThreadDeath fatal) {
            throw fatal;
        }
    }

    private static void throwIfFatal(Throwable failure) {
        if (failure instanceof VirtualMachineError fatal) {
            throw fatal;
        }
        if (failure instanceof ThreadDeath fatal) {
            throw fatal;
        }
    }

    @FunctionalInterface
    private interface Cleanup {
        void run() throws Throwable;
    }

    private static final class FatalFailure {
        private Throwable failure;

        private void record(Throwable candidate) {
            if (failure == null) {
                failure = candidate;
            } else {
                failure.addSuppressed(candidate);
            }
        }
    }
}
