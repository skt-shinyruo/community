package com.nowcoder.community.common.observability.http;

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
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ServletAccessRuntimeLogFilterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger("test.servlet-access-runtime");
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
    void skipsConfiguredExcludedPath() throws Exception {
        appender.start();
        logger.addAppender(appender);
        properties.getHttp().setSlowRequestThresholdMs(0);
        ServletAccessRuntimeLogFilter filter = new ServletAccessRuntimeLogFilter(writer, properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(appender.list).isEmpty();
    }

    @Test
    void logsOnlySlowRequestsWithSanitizedPath() throws Exception {
        appender.start();
        logger.addAppender(appender);
        properties.getHttp().setSlowRequestThresholdMs(100);
        ServletAccessRuntimeLogFilter filter = new ServletAccessRuntimeLogFilter(writer, properties);

        filter.logCompletedRequest("GET", "/api/posts", 200, 99);
        assertThat(appender.list).isEmpty();

        filter.logCompletedRequest("GET", "/api/posts?token=secret", 503, 101);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getMDCPropertyMap())
                .containsEntry(RuntimeLogFields.EVENT_CATEGORY, "access")
                .containsEntry(RuntimeLogFields.EVENT_ACTION, "http_slow_request")
                .containsEntry(RuntimeLogFields.EVENT_OUTCOME, "failure")
                .containsEntry("http.request.method", "GET")
                .containsEntry("url.path", "/api/posts")
                .containsEntry("http.response.status_code", "503")
                .containsEntry(RuntimeLogFields.DURATION_MS, "101")
                .containsEntry(RuntimeLogFields.THRESHOLD_MS, "100");
        assertThat(event.getFormattedMessage())
                .contains("http slow request")
                .contains("url.path=/api/posts")
                .doesNotContain("token=secret")
                .doesNotContain("Authorization")
                .doesNotContain("Cookie");
    }

    @Test
    void usesDeniedOutcomeForUnauthorizedAndForbiddenSlowRequests() throws Exception {
        appender.start();
        logger.addAppender(appender);
        properties.getHttp().setSlowRequestThresholdMs(1);
        ServletAccessRuntimeLogFilter filter = new ServletAccessRuntimeLogFilter(writer, properties);

        filter.logCompletedRequest("POST", "/api/admin/users", 403, 2);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getMDCPropertyMap())
                .containsEntry(RuntimeLogFields.EVENT_OUTCOME, "denied")
                .containsEntry("http.response.status_code", "403");
    }
}
