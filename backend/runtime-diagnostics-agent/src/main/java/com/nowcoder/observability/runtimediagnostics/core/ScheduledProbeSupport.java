package com.nowcoder.observability.runtimediagnostics.core;

import java.time.Duration;

public final class ScheduledProbeSupport {

    private ScheduledProbeSupport() {
    }

    public static Thread startDaemon(String threadName, Duration interval, Runnable task) {
        long sleepMillis = Math.max(1_000, interval == null ? 60_000 : interval.toMillis());
        Thread thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(sleepMillis);
                    task.run();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException ignored) {
                }
            }
        }, threadName);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
