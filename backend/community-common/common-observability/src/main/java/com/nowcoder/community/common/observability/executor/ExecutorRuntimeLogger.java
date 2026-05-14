package com.nowcoder.community.common.observability.executor;

import com.nowcoder.community.common.observability.logging.RuntimeLogEvent;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

public class ExecutorRuntimeLogger {

    private final RuntimeLogWriter logWriter;
    private final RuntimeLoggingProperties properties;
    private final Map<String, java.util.concurrent.Executor> executors;

    public ExecutorRuntimeLogger(
            RuntimeLogWriter logWriter,
            RuntimeLoggingProperties properties,
            Map<String, java.util.concurrent.Executor> executors
    ) {
        this.logWriter = logWriter;
        this.properties = properties;
        this.executors = executors;
    }

    public RuntimeLogWriter logWriter() {
        return logWriter;
    }

    public RuntimeLoggingProperties properties() {
        return properties;
    }

    public Map<String, java.util.concurrent.Executor> executors() {
        return executors;
    }

    public void logExecutorSnapshots() {
        executors.forEach((name, executor) -> snapshot(name, executor).ifPresent(this::logSnapshot));
    }

    public boolean logSnapshot(ExecutorSnapshot snapshot) {
        int thresholdPercent = properties.getExecutors().getSaturationThresholdPercent();
        int activePercent = snapshot.poolSize() <= 0 ? 0 : snapshot.active() * 100 / snapshot.poolSize();
        boolean activeThresholdReached = snapshot.poolSize() > 0 && activePercent >= thresholdPercent;
        boolean queuePressure = snapshot.queueSize() > 0;
        if (!activeThresholdReached && !queuePressure) {
            return false;
        }
        logWriter.warn(RuntimeLogEvent.builder("runtime", "executor_pressure", "threshold", "executor pressure")
                .field("executor.name", snapshot.name())
                .field("executor.active", snapshot.active())
                .field("executor.pool.size", snapshot.poolSize())
                .field("executor.queue.size", snapshot.queueSize())
                .field("executor.queue.remaining", snapshot.queueRemaining())
                .field("executor.rejected.count", snapshot.rejectedCount())
                .field("executor.active.percent", activePercent)
                .field(RuntimeLogFields.THRESHOLD_PERCENT, thresholdPercent)
                .build());
        return true;
    }

    public record ExecutorSnapshot(
            String name,
            int active,
            int poolSize,
            int queueSize,
            int queueRemaining,
            long rejectedCount
    ) {
    }

    private java.util.Optional<ExecutorSnapshot> snapshot(String name, java.util.concurrent.Executor executor) {
        if (executor instanceof ThreadPoolTaskExecutor taskExecutor) {
            ThreadPoolExecutor threadPoolExecutor = taskExecutor.getThreadPoolExecutor();
            return java.util.Optional.of(snapshot(name, threadPoolExecutor));
        }
        if (executor instanceof ThreadPoolExecutor threadPoolExecutor) {
            return java.util.Optional.of(snapshot(name, threadPoolExecutor));
        }
        if (executor instanceof ThreadPoolTaskScheduler taskScheduler) {
            ScheduledThreadPoolExecutor scheduledExecutor = taskScheduler.getScheduledThreadPoolExecutor();
            return java.util.Optional.of(new ExecutorSnapshot(
                    name,
                    scheduledExecutor.getActiveCount(),
                    scheduledExecutor.getPoolSize(),
                    scheduledExecutor.getQueue().size(),
                    scheduledExecutor.getQueue().remainingCapacity(),
                    0
            ));
        }
        return java.util.Optional.empty();
    }

    private ExecutorSnapshot snapshot(String name, ThreadPoolExecutor executor) {
        return new ExecutorSnapshot(
                name,
                executor.getActiveCount(),
                executor.getPoolSize(),
                executor.getQueue().size(),
                executor.getQueue().remainingCapacity(),
                0
        );
    }
}
