package com.nowcoder.community.common.observability.jvm;

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

import static org.assertj.core.api.Assertions.assertThat;

class JvmRuntimeLoggerTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger("test.jvm-runtime");
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
    void logsStartupSummaryWithStableJvmFields() {
        appender.start();
        logger.addAppender(appender);
        JvmRuntimeLogger runtimeLogger = new JvmRuntimeLogger(writer, properties);

        runtimeLogger.logStartupSummary();

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getMDCPropertyMap())
                .containsEntry(RuntimeLogFields.COMMUNITY_CATEGORY, "runtime")
                .containsEntry(RuntimeLogFields.COMMUNITY_ACTION, "jvm_startup")
                .containsEntry(RuntimeLogFields.COMMUNITY_OUTCOME, "success")
                .containsKey("jvm.version")
                .containsKey("jvm.available.processors")
                .containsKey("jvm.heap.max.bytes")
                .containsKey("process.timezone")
                .containsKey("process.charset");
        assertThat(event.getFormattedMessage())
                .contains("jvm startup summary")
                .contains("jvm.version=")
                .contains("jvm.heap.max.bytes=");
    }

    @Test
    void logsMemoryPressureOnlyWhenThresholdIsReached() {
        appender.start();
        logger.addAppender(appender);
        properties.getJvm().setMemoryThresholdPercent(85);
        JvmRuntimeLogger runtimeLogger = new JvmRuntimeLogger(writer, properties);

        assertThat(runtimeLogger.logMemoryPressure("heap", 84, 100)).isFalse();
        assertThat(appender.list).isEmpty();

        assertThat(runtimeLogger.logMemoryPressure("heap", 86, 100)).isTrue();

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getMDCPropertyMap())
                .containsEntry(RuntimeLogFields.COMMUNITY_CATEGORY, "runtime")
                .containsEntry(RuntimeLogFields.COMMUNITY_ACTION, "jvm_memory_pressure")
                .containsEntry(RuntimeLogFields.COMMUNITY_OUTCOME, "threshold")
                .containsEntry("jvm.memory.area", "heap")
                .containsEntry("jvm.memory.used.bytes", "86")
                .containsEntry("jvm.memory.max.bytes", "100")
                .containsEntry("jvm.memory.used.percent", "86")
                .containsEntry(RuntimeLogFields.THRESHOLD_PERCENT, "85");
    }

    @Test
    void ignoresMemoryPressureWhenMaxIsUnknown() {
        appender.start();
        logger.addAppender(appender);
        JvmRuntimeLogger runtimeLogger = new JvmRuntimeLogger(writer, properties);

        assertThat(runtimeLogger.logMemoryPressure("heap", 10, -1)).isFalse();

        assertThat(appender.list).isEmpty();
    }
}
