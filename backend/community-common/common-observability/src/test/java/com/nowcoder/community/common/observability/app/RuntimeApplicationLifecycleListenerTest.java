package com.nowcoder.community.common.observability.app;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogTestSupport;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.ContextClosedEvent;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RuntimeApplicationLifecycleListenerTest {

    @Test
    void logsStartupReadyShutdownAndTimeoutFromSpringEvents() throws Exception {
        try (RuntimeLogTestSupport.Capture capture = RuntimeLogTestSupport.capture("test.lifecycle-listener-runtime")) {
            ApplicationLifecycleRuntimeLogger logger = new ApplicationLifecycleRuntimeLogger(
                    capture.writer(),
                    new RuntimeLoggingProperties(),
                    "community-app",
                    "0.0.1-test",
                    List.of("test"),
                    8080
            );
            AtomicLong now = new AtomicLong(0);
            LongSupplier clock = now::get;
            RuntimeApplicationLifecycleListener listener = new RuntimeApplicationLifecycleListener(
                    logger,
                    Duration.ofMillis(10),
                    clock
            );

            listener.onApplicationEvent(new ApplicationStartedEvent(mock(), null, null, Duration.ofMillis(123)));
            listener.onApplicationEvent(new ApplicationReadyEvent(mock(), null, null, Duration.ofMillis(456)));
            listener.onApplicationEvent(new ContextClosedEvent(mock()));
            now.set(Duration.ofMillis(15).toNanos());
            listener.destroy();

            assertThat(capture.appender().list).extracting(event -> event.getMDCPropertyMap().get(RuntimeLogFields.COMMUNITY_ACTION))
                    .containsExactly("app_startup", "app_ready", "app_shutdown", "graceful_shutdown_timeout");
            ILoggingEvent shutdown = capture.appender().list.get(2);
            assertThat(shutdown.getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.COMMUNITY_OUTCOME, "success")
                    .containsEntry(RuntimeLogFields.DURATION_MS, "15");
        }
    }
}
