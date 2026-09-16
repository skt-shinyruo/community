package com.nowcoder.community.search.infrastructure.reindex;

import com.nowcoder.community.infra.scheduler.SingleFlightTaskGuard;
import com.nowcoder.community.search.application.SearchReindexLeasePort;
import com.nowcoder.community.search.infrastructure.LeaseRenewalScheduler;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class RedisSearchReindexLeaseAdapter implements SearchReindexLeasePort {

    private static final String TASK_NAME = "search:reindex";

    private final SingleFlightTaskGuard taskGuard;
    private final ScheduledExecutorService renewer;
    private final boolean ownsRenewer;

    @Autowired
    public RedisSearchReindexLeaseAdapter(SingleFlightTaskGuard taskGuard) {
        this(taskGuard, newRenewer(), true);
    }

    RedisSearchReindexLeaseAdapter(
            SingleFlightTaskGuard taskGuard,
            ScheduledExecutorService renewer,
            boolean ownsRenewer
    ) {
        this.taskGuard = taskGuard;
        this.renewer = renewer;
        this.ownsRenewer = ownsRenewer;
    }

    @Override
    public Optional<Lease> tryAcquire(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return Optional.empty();
        }
        SingleFlightTaskGuard.Lock lock = taskGuard.tryAcquire(TASK_NAME, ttl);
        if (lock == null) {
            return Optional.empty();
        }

        AtomicBoolean valid = new AtomicBoolean(true);
        AtomicBoolean closed = new AtomicBoolean(false);
        try {
            ScheduledFuture<?> renewal = LeaseRenewalScheduler.scheduleThirdOfTtl(
                    renewer,
                    ttl,
                    () -> {
                        if (!closed.get() && valid.get() && !taskGuard.refresh(lock, ttl)) {
                            valid.set(false);
                        }
                    }
            );
            return Optional.of(new GuardedLease(lock, renewal, valid, closed));
        } catch (RuntimeException schedulingFailure) {
            taskGuard.release(lock);
            return Optional.empty();
        }
    }

    @PreDestroy
    void shutdown() {
        if (ownsRenewer) {
            renewer.shutdownNow();
        }
    }

    private final class GuardedLease implements Lease {

        private final SingleFlightTaskGuard.Lock lock;
        private final ScheduledFuture<?> renewal;
        private final AtomicBoolean valid;
        private final AtomicBoolean closed;

        private GuardedLease(
                SingleFlightTaskGuard.Lock lock,
                ScheduledFuture<?> renewal,
                AtomicBoolean valid,
                AtomicBoolean closed
        ) {
            this.lock = lock;
            this.renewal = renewal;
            this.valid = valid;
            this.closed = closed;
        }

        @Override
        public boolean isValid() {
            return valid.get() && !closed.get();
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            valid.set(false);
            renewal.cancel(false);
            taskGuard.release(lock);
        }
    }

    private static ScheduledExecutorService newRenewer() {
        return LeaseRenewalScheduler.newDaemonScheduler("search-reindex-lock-renewer");
    }
}
