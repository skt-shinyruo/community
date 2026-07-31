package com.nowcoder.yierloom.plugins.thread;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

import com.nowcoder.yierloom.api.EventSink;
import com.nowcoder.yierloom.api.ManagedTask;
import com.nowcoder.yierloom.api.PluginDescriptor;
import com.nowcoder.yierloom.api.PluginRuntimeContext;
import com.nowcoder.yierloom.api.RuntimeCapability;
import com.nowcoder.yierloom.api.YierLoomApi;
import com.nowcoder.yierloom.api.YierLoomPlugin;
import com.nowcoder.yierloom.plugins.support.PluginSettings;

public final class ThreadPlugin implements YierLoomPlugin, RuntimeCapability {
    private static final Set<String> CONFIG_KEYS = Set.of("enabled", "snapshot-interval");
    private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(60);

    private final Object emissionMonitor = new Object();
    private volatile ManagedTask task;
    private boolean active;

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(
                "thread",
                "Thread Diagnostics",
                "1.0.0",
                YierLoomApi.VERSION,
                true,
                300);
    }

    @Override
    public synchronized void start(PluginRuntimeContext context) {
        Objects.requireNonNull(context, "context");
        if (task != null) {
            throw new IllegalStateException("thread plugin is already started");
        }
        PluginSettings.validateKeys(context.config(), CONFIG_KEYS);
        Duration interval = PluginSettings.positiveDuration(
                context.config(), "snapshot-interval", DEFAULT_INTERVAL);
        ThreadSnapshotReporter reporter = new ThreadSnapshotReporter(
                ManagementFactory.getThreadMXBean());
        EventSink events = context.events();
        synchronized (emissionMonitor) {
            active = true;
        }
        try {
            task = Objects.requireNonNull(
                    context.scheduler().scheduleWithFixedDelay(
                            "thread-snapshot",
                            interval,
                            interval,
                            () -> reportIfActive(reporter, events)),
                    "scheduled task");
        } catch (RuntimeException | Error failure) {
            synchronized (emissionMonitor) {
                active = false;
            }
            throw failure;
        }
    }

    @Override
    public synchronized void stop() {
        ManagedTask current = task;
        task = null;
        synchronized (emissionMonitor) {
            active = false;
        }
        if (current != null) {
            current.cancel();
        }
    }

    private void reportIfActive(ThreadSnapshotReporter reporter, EventSink events) {
        synchronized (emissionMonitor) {
            if (active) {
                reporter.report(events);
            }
        }
    }
}
