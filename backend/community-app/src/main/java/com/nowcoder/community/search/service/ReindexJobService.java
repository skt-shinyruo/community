package com.nowcoder.community.search.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.search.exception.SearchErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 单进程 reindex single-flight + jobId。
 */
@Service
public class ReindexJobService {

    private final AtomicReference<String> runningJobId = new AtomicReference<>();

    public ReindexJob tryStart() {
        String newJobId = newJobId();
        if (runningJobId.compareAndSet(null, newJobId)) {
            return new ReindexJob(newJobId, true);
        }
        return new ReindexJob(currentJobId(), false);
    }

    public RenewalHandle startRenewal(String jobId) {
        return () -> { };
    }

    public void finish(String jobId) {
        if (!StringUtils.hasText(jobId)) {
            return;
        }
        runningJobId.compareAndSet(jobId.trim(), null);
    }

    public void conflict(String jobId) {
        String suffix = StringUtils.hasText(jobId) ? (" (jobId=" + jobId.trim() + ")") : "";
        throw new BusinessException(SearchErrorCode.REINDEX_RUNNING, "reindex 任务正在执行" + suffix);
    }

    private String currentJobId() {
        String value = runningJobId.get();
        return StringUtils.hasText(value) ? value.trim() : null;
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

    public record ReindexJob(String jobId, boolean acquired) {
    }
}
