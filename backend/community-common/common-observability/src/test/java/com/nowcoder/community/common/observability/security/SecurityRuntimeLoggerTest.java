package com.nowcoder.community.common.observability.security;

import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogTestSupport;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityRuntimeLoggerTest {

    @Test
    void logsRateLimitAndAuthFilterErrorsWithoutIdentityValues() {
        try (RuntimeLogTestSupport.Capture capture = RuntimeLogTestSupport.capture("test.security-runtime")) {
            SecurityRuntimeLogger logger = new SecurityRuntimeLogger(capture.writer(), new RuntimeLoggingProperties());

            logger.logRateLimitTriggered("login", "ip", "/api/auth/login", 20, 20);
            logger.logAuthFilterError("origin_guard", "/api/auth/login", new IllegalStateException("bad origin https://secret.example"));

            assertThat(capture.appender().list).hasSize(2);
            assertThat(capture.appender().list.get(0).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.COMMUNITY_CATEGORY, "security")
                    .containsEntry(RuntimeLogFields.COMMUNITY_ACTION, "rate_limit_triggered")
                    .containsEntry("security.control", "login")
                    .containsEntry("security.subject.type", "ip")
                    .containsEntry("url.path", "/api/auth/login")
                    .containsEntry("rate.limit.current", "20")
                    .containsEntry("rate.limit.threshold", "20");
            assertThat(capture.appender().list.get(1).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.COMMUNITY_ACTION, "auth_filter_error")
                    .containsEntry("security.control", "origin_guard")
                    .containsEntry("error.type", IllegalStateException.class.getName());
            assertThat(capture.appender().list.get(1).getFormattedMessage()).doesNotContain("secret.example");
        }
    }
}
