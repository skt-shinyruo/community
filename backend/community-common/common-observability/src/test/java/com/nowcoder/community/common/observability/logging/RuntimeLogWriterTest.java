package com.nowcoder.community.common.observability.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeLogWriterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger("test.runtime-log-writer");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        MDC.clear();
    }

    @Test
    void writesRuntimeEventFieldsIntoMdcAndRestoresPreviousValues() {
        appender.start();
        logger.addAppender(appender);
        RuntimeLogWriter writer = new RuntimeLogWriter(logger);
        MDC.put(RuntimeLogFields.COMMUNITY_CATEGORY, "previous-category");
        MDC.put(RuntimeLogFields.COMMUNITY_ACTION, "previous-action");
        MDC.put(RuntimeLogFields.COMMUNITY_OUTCOME, "previous-outcome");
        MDC.put(RuntimeLogFields.DURATION_MS, "previous-duration");

        writer.info(RuntimeLogEvent.builder("runtime", "jvm_startup", "success", "jvm startup summary")
                .field(RuntimeLogFields.DURATION_MS, 42)
                .field("jvm.version", "17.0.12")
                .build());

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getMDCPropertyMap())
                .containsEntry(RuntimeLogFields.COMMUNITY_CATEGORY, "runtime")
                .containsEntry(RuntimeLogFields.COMMUNITY_ACTION, "jvm_startup")
                .containsEntry(RuntimeLogFields.COMMUNITY_OUTCOME, "success")
                .containsEntry(RuntimeLogFields.EVENT_CATEGORY, "runtime")
                .containsEntry(RuntimeLogFields.EVENT_ACTION, "jvm_startup")
                .containsEntry(RuntimeLogFields.EVENT_OUTCOME, "success")
                .containsEntry(RuntimeLogFields.DURATION_MS, "42")
                .containsEntry("jvm.version", "17.0.12");
        assertThat(event.getFormattedMessage())
                .contains("jvm startup summary")
                .contains("duration.ms=42")
                .contains("jvm.version=17.0.12")
                .doesNotContain("community.category=")
                .doesNotContain("community.action=")
                .doesNotContain("community.outcome=");

        assertThat(MDC.get(RuntimeLogFields.COMMUNITY_CATEGORY)).isEqualTo("previous-category");
        assertThat(MDC.get(RuntimeLogFields.COMMUNITY_ACTION)).isEqualTo("previous-action");
        assertThat(MDC.get(RuntimeLogFields.COMMUNITY_OUTCOME)).isEqualTo("previous-outcome");
        assertThat(MDC.get(RuntimeLogFields.DURATION_MS)).isEqualTo("previous-duration");
        assertThat(MDC.get(RuntimeLogFields.EVENT_CATEGORY)).isNull();
        assertThat(MDC.get("jvm.version")).isNull();
    }

    @Test
    void skipsNullFieldsAndSupportsWarningEvents() {
        appender.start();
        logger.addAppender(appender);
        RuntimeLogWriter writer = new RuntimeLogWriter(logger);

        writer.warn(RuntimeLogEvent.builder("database", "hikari_pool_pressure", "threshold", "hikari pool pressure")
                .field("db.pool.name", "HikariPool-1")
                .field("db.pool.pending", null)
                .build());

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getMDCPropertyMap())
                .containsEntry(RuntimeLogFields.COMMUNITY_CATEGORY, "database")
                .containsEntry(RuntimeLogFields.EVENT_ACTION, "hikari_pool_pressure")
                .containsEntry("db.pool.name", "HikariPool-1")
                .doesNotContainKey("db.pool.pending");
        assertThat(event.getFormattedMessage())
                .contains("hikari pool pressure")
                .contains("db.pool.name=HikariPool-1")
                .doesNotContain("db.pool.pending=");
    }
}
