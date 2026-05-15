package com.nowcoder.community.common.observability.autoconfig;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nowcoder.community.common.observability.jvm.JvmRuntimeLogger;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeObservabilityTriggerTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger("com.nowcoder.community.runtime");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RuntimeObservabilityAutoConfiguration.class));

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        MDC.clear();
    }

    @Test
    void startupTriggerEmitsJvmStartupSummaryWhenEnabled() {
        appender.start();
        logger.addAppender(appender);

        contextRunner.run(context -> {
            RuntimeStartupLogger startupLogger = context.getBean(RuntimeStartupLogger.class);
            startupLogger.logStartupSummary();
        });

        assertThat(appender.list)
                .anySatisfy(event -> assertThat(event.getMDCPropertyMap())
                        .containsEntry(RuntimeLogFields.EVENT_CATEGORY, "runtime")
                        .containsEntry(RuntimeLogFields.EVENT_ACTION, "jvm_startup")
                        .containsEntry(RuntimeLogFields.EVENT_ACTION, "jvm_startup"));
    }

    @Test
    void startupTriggerCanBeDisabledIndependently() {
        contextRunner
                .withPropertyValues("community.observability.runtime-logging.startup-summary-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(JvmRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(RuntimeStartupLogger.class);
                });
    }

    @Test
    void periodicTriggerCanBeDisabledIndependently() {
        contextRunner
                .withPropertyValues("community.observability.runtime-logging.periodic-summary-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(JvmRuntimeLogger.class);
                    assertThat(context).doesNotHaveBean(RuntimeSnapshotScheduler.class);
                });
    }
}
