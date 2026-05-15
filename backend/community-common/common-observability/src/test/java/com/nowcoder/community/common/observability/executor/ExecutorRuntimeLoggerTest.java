package com.nowcoder.community.common.observability.executor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutorRuntimeLoggerTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger("test.executor-runtime");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final RuntimeLoggingProperties properties = new RuntimeLoggingProperties();
    private final RuntimeLogWriter writer = new RuntimeLogWriter(logger);

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        MDC.clear();
    }

    @Test
    void logsExecutorPressureOnlyWhenActiveThresholdIsReached() {
        appender.start();
        logger.addAppender(appender);
        properties.getExecutors().setSaturationThresholdPercent(80);
        ExecutorRuntimeLogger runtimeLogger = new ExecutorRuntimeLogger(writer, properties, Map.of());

        assertThat(runtimeLogger.logSnapshot(new ExecutorRuntimeLogger.ExecutorSnapshot(
                "applicationTaskExecutor", 7, 10, 0, 100, 0
        ))).isFalse();
        assertThat(appender.list).isEmpty();

        assertThat(runtimeLogger.logSnapshot(new ExecutorRuntimeLogger.ExecutorSnapshot(
                "applicationTaskExecutor", 8, 10, 3, 100, 0
        ))).isTrue();

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getMDCPropertyMap())
                .containsEntry(RuntimeLogFields.EVENT_CATEGORY, "runtime")
                .containsEntry(RuntimeLogFields.EVENT_ACTION, "executor_pressure")
                .containsEntry(RuntimeLogFields.EVENT_OUTCOME, "threshold")
                .containsEntry("executor.name", "applicationTaskExecutor")
                .containsEntry("executor.active", "8")
                .containsEntry("executor.pool.size", "10")
                .containsEntry("executor.queue.size", "3")
                .containsEntry("executor.queue.remaining", "100")
                .containsEntry("executor.rejected.count", "0")
                .containsEntry("executor.active.percent", "80")
                .containsEntry(RuntimeLogFields.THRESHOLD_PERCENT, "80");
    }

    @Test
    void logsExecutorPressureWhenQueueHasPendingWorkAtThreshold() {
        appender.start();
        logger.addAppender(appender);
        ExecutorRuntimeLogger runtimeLogger = new ExecutorRuntimeLogger(writer, properties, Map.of());

        assertThat(runtimeLogger.logSnapshot(new ExecutorRuntimeLogger.ExecutorSnapshot(
                "scheduler", 0, 0, 2, 0, 0
        ))).isTrue();

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getMDCPropertyMap())
                .containsEntry("executor.name", "scheduler")
                .containsEntry("executor.queue.size", "2");
    }
}
