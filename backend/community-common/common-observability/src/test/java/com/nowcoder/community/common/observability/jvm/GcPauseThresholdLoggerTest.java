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

class GcPauseThresholdLoggerTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger("test.gc-runtime");
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
    void logsGcPauseOnlyWhenThresholdIsReached() {
        appender.start();
        logger.addAppender(appender);
        properties.getJvm().setGcPauseThresholdMs(200);
        GcPauseThresholdLogger runtimeLogger = new GcPauseThresholdLogger(writer, properties);

        assertThat(runtimeLogger.logGcPause("G1 Young Generation", 199)).isFalse();
        assertThat(appender.list).isEmpty();

        assertThat(runtimeLogger.logGcPause("G1 Young Generation", 201)).isTrue();

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getMDCPropertyMap())
                .containsEntry(RuntimeLogFields.COMMUNITY_CATEGORY, "runtime")
                .containsEntry(RuntimeLogFields.COMMUNITY_ACTION, "jvm_gc_pause_threshold")
                .containsEntry(RuntimeLogFields.COMMUNITY_OUTCOME, "threshold")
                .containsEntry("jvm.gc.name", "G1 Young Generation")
                .containsEntry("jvm.gc.pause.ms", "201")
                .containsEntry(RuntimeLogFields.THRESHOLD_MS, "200");
    }
}
