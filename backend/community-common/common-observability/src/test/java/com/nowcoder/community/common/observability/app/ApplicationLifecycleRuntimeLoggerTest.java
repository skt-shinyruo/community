package com.nowcoder.community.common.observability.app;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogTestSupport;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationLifecycleRuntimeLoggerTest {

    @Test
    void logsStartupReadyShutdownAndGracefulShutdownTimeoutWithoutConfigSecrets() {
        try (RuntimeLogTestSupport.Capture capture = RuntimeLogTestSupport.capture("test.lifecycle-runtime")) {
            ApplicationLifecycleRuntimeLogger logger = new ApplicationLifecycleRuntimeLogger(
                    capture.writer(),
                    new RuntimeLoggingProperties(),
                    "community-app",
                    "0.0.1-test",
                    List.of("local", "test"),
                    8080
            );

            logger.logStartup(Duration.ofMillis(123));
            logger.logReady(Duration.ofMillis(456));
            logger.logShutdown(Duration.ofMillis(789));
            logger.logGracefulShutdownTimeout(Duration.ofSeconds(30));

            assertThat(capture.appender().list).hasSize(4);
            assertEvent(capture.appender().list.get(0), "app_startup", "success");
            assertEvent(capture.appender().list.get(1), "app_ready", "success");
            assertEvent(capture.appender().list.get(2), "app_shutdown", "success");
            assertEvent(capture.appender().list.get(3), "graceful_shutdown_timeout", "threshold");
            assertThat(capture.appender().list.get(0).getMDCPropertyMap())
                    .containsEntry("service.name", "community-app")
                    .containsEntry("service.version", "0.0.1-test")
                    .containsEntry("spring.profiles.active", "local,test")
                    .containsEntry("server.port", "8080")
                    .containsEntry("duration.ms", "123")
                    .containsEntry("jvm.version", System.getProperty("java.version"));
            assertThat(capture.appender().list.get(0).getMDCPropertyMap().keySet())
                    .noneMatch(key -> key.toLowerCase().contains("password"))
                    .noneMatch(key -> key.toLowerCase().contains("secret"));
        }
    }

    private void assertEvent(ILoggingEvent event, String action, String outcome) {
        Map<String, String> mdc = event.getMDCPropertyMap();
        assertThat(mdc)
                .containsEntry(RuntimeLogFields.EVENT_CATEGORY, "runtime")
                .containsEntry(RuntimeLogFields.EVENT_ACTION, action)
                .containsEntry(RuntimeLogFields.EVENT_OUTCOME, outcome);
    }
}
