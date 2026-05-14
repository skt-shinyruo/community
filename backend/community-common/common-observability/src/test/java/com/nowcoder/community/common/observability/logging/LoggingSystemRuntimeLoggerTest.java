package com.nowcoder.community.common.observability.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingSystemRuntimeLoggerTest {

    @Test
    void logsAppenderErrorsAndQueuePressure() {
        try (RuntimeLogTestSupport.Capture capture = RuntimeLogTestSupport.capture("test.logging-system-runtime")) {
            RuntimeLoggingProperties properties = new RuntimeLoggingProperties();
            properties.getLoggingSystem().setQueuePressureThresholdPercent(80);
            LoggingSystemRuntimeLogger logger = new LoggingSystemRuntimeLogger(capture.writer(), properties);

            logger.logAppenderError("FILE_JSON", new IllegalStateException("disk full"));
            assertThat(logger.logQueuePressure("ASYNC_JSON", 79, 100)).isFalse();
            assertThat(logger.logQueuePressure("ASYNC_JSON", 81, 100)).isTrue();

            assertThat(capture.appender().list).hasSize(2);
            assertThat(capture.appender().list.get(0).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.COMMUNITY_CATEGORY, "logging")
                    .containsEntry(RuntimeLogFields.COMMUNITY_ACTION, "logging_appender_error")
                    .containsEntry("logging.appender.name", "FILE_JSON")
                    .containsEntry("error.type", IllegalStateException.class.getName());
            assertThat(capture.appender().list.get(1).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.COMMUNITY_ACTION, "logging_queue_pressure")
                    .containsEntry("logging.queue.name", "ASYNC_JSON")
                    .containsEntry("logging.queue.used.percent", "81")
                    .containsEntry("threshold.percent", "80");
        }
    }
}
