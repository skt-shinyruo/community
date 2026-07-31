package com.nowcoder.yierloom.plugins.jvm;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.nowcoder.yierloom.api.EventSink;
import com.nowcoder.yierloom.api.ManagedTask;
import com.nowcoder.yierloom.api.PluginConfig;
import com.nowcoder.yierloom.api.PluginDescriptor;
import com.nowcoder.yierloom.api.PluginRuntimeContext;
import com.nowcoder.yierloom.api.RuntimeCapability;
import com.nowcoder.yierloom.api.YierLoomApi;
import com.nowcoder.yierloom.api.YierLoomPlugin;
import com.nowcoder.yierloom.plugins.support.PluginSettings;

public final class JvmPlugin implements YierLoomPlugin, RuntimeCapability {
    private static final Set<String> CONFIG_KEYS = Set.of("enabled", "summary-interval");

    private volatile JvmRuntime runtime;

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(
                "jvm",
                "JVM Diagnostics",
                "1.0.0",
                YierLoomApi.VERSION,
                true,
                310);
    }

    @Override
    public synchronized void start(PluginRuntimeContext context) {
        if (runtime != null) {
            throw new IllegalStateException("jvm plugin is already started");
        }
        Objects.requireNonNull(context, "context");
        JvmSettings settings = JvmSettings.from(context.config());
        runtime = JvmRuntime.start(context, settings.summaryInterval());
    }

    @Override
    public synchronized void stop() {
        JvmRuntime current = runtime;
        runtime = null;
        if (current != null) {
            current.close();
        }
    }

    record JvmSettings(Duration summaryInterval) {
        static JvmSettings from(PluginConfig config) {
            PluginSettings.validateKeys(config, CONFIG_KEYS);
            return new JvmSettings(PluginSettings.positiveDuration(
                    config, "summary-interval", Duration.ofSeconds(60)));
        }
    }

    private static final class JvmRuntime implements AutoCloseable {
        private final EventSink events;
        private final JvmRuntimeReporter reporter;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final Object emissionLock = new Object();
        private volatile ManagedTask task;

        private JvmRuntime(EventSink events, JvmRuntimeReporter reporter) {
            this.events = Objects.requireNonNull(events, "events");
            this.reporter = Objects.requireNonNull(reporter, "reporter");
        }

        private static JvmRuntime start(PluginRuntimeContext context, Duration interval) {
            JvmRuntime runtime = new JvmRuntime(context.events(), new JvmRuntimeReporter());
            try {
                runtime.task = Objects.requireNonNull(
                        context.scheduler().scheduleWithFixedDelay(
                                "jvm-summary", interval, interval, runtime::report),
                        "scheduler returned a null task");
                return runtime;
            } catch (Throwable failure) {
                try {
                    runtime.close();
                } catch (Throwable cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                if (failure instanceof RuntimeException runtimeFailure) {
                    throw runtimeFailure;
                }
                if (failure instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException("jvm runtime could not start", failure);
            }
        }

        private void report() {
            synchronized (emissionLock) {
                if (active.get()) {
                    reporter.report(events);
                }
            }
        }

        @Override
        public void close() {
            if (!active.compareAndSet(true, false)) {
                return;
            }
            ManagedTask current = task;
            task = null;
            try {
                if (current != null) {
                    current.cancel();
                }
            } finally {
                synchronized (emissionLock) {
                    // Wait for a report admitted before the active flag changed.
                }
            }
        }
    }
}
