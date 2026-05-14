package com.nowcoder.community.common.observability.autoconfig;

import com.nowcoder.community.common.observability.data.DataSourceRuntimeLogger;
import com.nowcoder.community.common.observability.executor.ExecutorRuntimeLogger;
import com.nowcoder.community.common.observability.jvm.JvmExtendedRuntimeLogger;
import com.nowcoder.community.common.observability.jvm.JvmRuntimeLogger;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import com.nowcoder.community.common.observability.system.ProcessResourceRuntimeLogger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

public class RuntimeSnapshotScheduler implements SchedulingConfigurer {

    private final JvmRuntimeLogger jvmRuntimeLogger;
    private final ObjectProvider<JvmExtendedRuntimeLogger> jvmExtendedRuntimeLogger;
    private final ObjectProvider<ExecutorRuntimeLogger> executorRuntimeLogger;
    private final ObjectProvider<DataSourceRuntimeLogger> dataSourceRuntimeLogger;
    private final ObjectProvider<ProcessResourceRuntimeLogger> processResourceRuntimeLogger;
    private final Duration periodicSummaryInterval;

    public RuntimeSnapshotScheduler(
            JvmRuntimeLogger jvmRuntimeLogger,
            ObjectProvider<JvmExtendedRuntimeLogger> jvmExtendedRuntimeLogger,
            ObjectProvider<ExecutorRuntimeLogger> executorRuntimeLogger,
            ObjectProvider<DataSourceRuntimeLogger> dataSourceRuntimeLogger,
            ObjectProvider<ProcessResourceRuntimeLogger> processResourceRuntimeLogger,
            RuntimeLoggingProperties properties
    ) {
        this.jvmRuntimeLogger = jvmRuntimeLogger;
        this.jvmExtendedRuntimeLogger = jvmExtendedRuntimeLogger;
        this.executorRuntimeLogger = executorRuntimeLogger;
        this.dataSourceRuntimeLogger = dataSourceRuntimeLogger;
        this.processResourceRuntimeLogger = processResourceRuntimeLogger;
        this.periodicSummaryInterval = normalizeInterval(properties.getPeriodicSummaryInterval());
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(new FixedDelayTask(
                this::logThresholdSnapshots,
                periodicSummaryInterval,
                periodicSummaryInterval
        ));
    }

    public void logThresholdSnapshots() {
        jvmRuntimeLogger.logMemoryPressureSnapshot();
        JvmExtendedRuntimeLogger jvmExtendedLogger = jvmExtendedRuntimeLogger.getIfAvailable();
        if (jvmExtendedLogger != null) {
            jvmExtendedLogger.logExtendedSnapshots();
        }
        ExecutorRuntimeLogger executorLogger = executorRuntimeLogger.getIfAvailable();
        if (executorLogger != null) {
            executorLogger.logExecutorSnapshots();
        }
        DataSourceRuntimeLogger dataSourceLogger = dataSourceRuntimeLogger.getIfAvailable();
        if (dataSourceLogger != null) {
            dataSourceLogger.logPoolSnapshot();
        }
        ProcessResourceRuntimeLogger processResourceLogger = processResourceRuntimeLogger.getIfAvailable();
        if (processResourceLogger != null) {
            processResourceLogger.logResourceSnapshots();
        }
    }

    private static Duration normalizeInterval(Duration interval) {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            return Duration.ofSeconds(60);
        }
        return interval;
    }
}
