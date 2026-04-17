package com.nowcoder.community.search.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.infra.scheduler.SingleFlightTaskGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import com.nowcoder.community.search.exception.SearchErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis-backed reindex single-flight + jobId.
 */
@Service
public class ReindexJobService {

    private static final String TASK_NAME = "search:reindex";

    private final SingleFlightTaskGuard singleFlightTaskGuard;
    private final Duration lockTtl;

    public ReindexJobService(
            ObjectProvider<SingleFlightTaskGuard> singleFlightTaskGuardProvider,
            @Value("${search.reindex.lock-ttl:30m}") Duration lockTtl
    ) {
        this(singleFlightTaskGuardProvider == null ? null : singleFlightTaskGuardProvider.getIfAvailable(), lockTtl);
    }

    ReindexJobService(SingleFlightTaskGuard singleFlightTaskGuard, Duration lockTtl) {
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
        return () -> { };
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
