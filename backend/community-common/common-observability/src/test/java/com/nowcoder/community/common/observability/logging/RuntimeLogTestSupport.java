package com.nowcoder.community.common.observability.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public final class RuntimeLogTestSupport {

    private RuntimeLogTestSupport() {
    }

    public static Capture capture(String loggerName) {
        return new Capture(loggerName);
    }

    public static final class Capture implements AutoCloseable {

        private final Logger logger;
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        private final RuntimeLogWriter writer;

        private Capture(String loggerName) {
            this.logger = (Logger) LoggerFactory.getLogger(loggerName);
            this.writer = new RuntimeLogWriter(logger);
            appender.start();
            logger.addAppender(appender);
        }

        public RuntimeLogWriter writer() {
            return writer;
        }

        public ListAppender<ILoggingEvent> appender() {
            return appender;
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
            MDC.clear();
        }
    }
}
