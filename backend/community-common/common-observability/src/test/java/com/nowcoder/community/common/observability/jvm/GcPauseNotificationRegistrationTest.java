package com.nowcoder.community.common.observability.jvm;

import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class GcPauseNotificationRegistrationTest {

    @Test
    void registersNotificationListenersAtBeanInitialization() {
        AtomicInteger registrations = new AtomicInteger();
        GcPauseThresholdLogger runtimeLogger = new GcPauseThresholdLogger(
                new RuntimeLogWriter(LoggerFactory.getLogger("test.gc-registration")),
                new RuntimeLoggingProperties(),
                () -> registrations.incrementAndGet()
        );

        runtimeLogger.afterPropertiesSet();
        runtimeLogger.afterPropertiesSet();

        assertThat(registrations).hasValue(1);
    }
}
