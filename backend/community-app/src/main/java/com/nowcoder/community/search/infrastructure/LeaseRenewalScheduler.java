package com.nowcoder.community.search.infrastructure;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Shared daemon scheduler support for the search lease-renewal loops
 * (reindex lock lease and rebuild-target registration lease).
 */
public final class LeaseRenewalScheduler {

    private LeaseRenewalScheduler() {
    }

    public static ScheduledExecutorService newDaemonScheduler(String threadName) {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    public static ScheduledFuture<?> scheduleThirdOfTtl(
            ScheduledExecutorService scheduler,
            Duration ttl,
            Runnable renewal
    ) {
        long intervalMs = Math.max(1L, ttl.dividedBy(3).toMillis());
        return scheduler.scheduleAtFixedRate(renewal, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }
}
