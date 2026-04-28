package com.nowcoder.community.search.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.infra.scheduler.SingleFlightTaskGuard;
import com.nowcoder.community.search.exception.SearchErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis-backed reindex single-flight + jobId.
 */
@Service
public class ReindexJobApplicationService {

    private static final String TASK_NAME = "search:reindex";

    private final SingleFlightTaskGuard singleFlightTaskGuard;
    private final Duration lockTtl;

    @Autowired
    public ReindexJobApplicationService(
            ObjectProvider<SingleFlightTaskGuard> singleFlightTaskGuardProvider,
            @Value("${search.reindex.lock-ttl:30m}") Duration lockTtl
    ) {
        this(singleFlightTaskGuardProvider == null ? null : singleFlightTaskGuardProvider.getIfAvailable(), lockTtl);
    }

    ReindexJobApplicationService(SingleFlightTaskGuard singleFlightTaskGuard, Duration lockTtl) {
        this.singleFlightTaskGuard = singleFlightTaskGuard;
        this.lockTtl = lockTtl == null || lockTtl.isNegative() || lockTtl.isZero() ? Duration.ofMinutes(30) : lockTtl;
    }

    public ReindexJob tryStart() {
        String jobId = newJobId();
        SingleFlightTaskGuard.Lock lock =
                singleFlightTaskGuard == null ? null : singleFlightTaskGuard.tryAcquire(TASK_NAME, lockTtl);
        if (lock == null) {
            return new ReindexJob(null, false, null);
        }
        return new ReindexJob(jobId, true, lock);
    }

    public RenewalHandle startRenewal(ReindexJob job) {
        if (job == null || job.lock() == null || singleFlightTaskGuard == null) {
            return () -> { };
        }

        SingleFlightTaskGuard.Lock lock = job.lock();

        // Immediate best-effort refresh so long-running jobs don't lose the lock right after start.
        singleFlightTaskGuard.refresh(lock, lockTtl);

        Duration interval = renewalInterval(lockTtl);
        long intervalMs = Math.max(1_000L, interval.toMillis());

        ScheduledExecutorService scheduler;
        try {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "search-reindex-lock-renewer");
                t.setDaemon(true);
                return t;
            });
        } catch (RuntimeException e) {
            return () -> { };
        }

        AtomicBoolean stopped = new AtomicBoolean(false);
        ScheduledFuture<?> scheduled = scheduler.scheduleAtFixedRate(() -> {
            if (stopped.get()) {
                return;
            }
            singleFlightTaskGuard.refresh(lock, lockTtl);
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);

        return () -> {
            stopped.set(true);
            if (scheduled != null) {
                scheduled.cancel(false);
            }
            try {
                scheduler.shutdownNow();
            } catch (RuntimeException ignored) {
            }
        };
    }

    public void finish(ReindexJob job) {
        if (job == null || job.lock() == null || singleFlightTaskGuard == null) {
            return;
        }
        singleFlightTaskGuard.release(job.lock());
    }

    public void conflict(String jobId) {
        String suffix = StringUtils.hasText(jobId) ? (" (jobId=" + jobId.trim() + ")") : "";
        throw new BusinessException(SearchErrorCode.REINDEX_RUNNING, "reindex 任务正在执行" + suffix);
    }

    private Duration renewalInterval(Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            return Duration.ofSeconds(60);
        }
        Duration interval = ttl.dividedBy(3);
        if (interval.isNegative() || interval.isZero()) {
            return Duration.ofSeconds(1);
        }
        return interval;
    }

    private String newJobId() {
        try {
            return UUID.randomUUID().toString();
        } catch (RuntimeException e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    @FunctionalInterface
    public interface RenewalHandle extends AutoCloseable {
        void stop();

        @Override
        default void close() {
            stop();
        }
    }

    public record ReindexJob(String jobId, boolean acquired, SingleFlightTaskGuard.Lock lock) {
    }
}
