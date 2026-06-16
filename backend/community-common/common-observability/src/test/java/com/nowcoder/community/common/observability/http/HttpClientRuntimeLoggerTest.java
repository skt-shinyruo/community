package com.nowcoder.community.common.observability.http;

import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogTestSupport;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpClientRuntimeLoggerTest {

    @Test
    void logsSlowAndFailedHttpClientCallsWithoutQueryOrAuthorization() {
        try (RuntimeLogTestSupport.Capture capture = RuntimeLogTestSupport.capture("test.http-client-runtime")) {
            RuntimeLoggingProperties properties = new RuntimeLoggingProperties();
            properties.getHttpClient().setSlowRequestThresholdMs(300);
            HttpClientRuntimeLogger logger = new HttpClientRuntimeLogger(capture.writer(), properties);

            assertThat(logger.logSlowRequest("oss", "GET", "http://community-oss:18090/api/oss/objects/1?token=secret", 200, 299)).isFalse();
            assertThat(logger.logSlowRequest("oss", "GET", "http://community-oss:18090/api/oss/objects/1?token=secret", 200, 301)).isTrue();
            logger.logClientError("oss", "POST", "http://community-oss:18090/api/oss/objects?token=secret", 503, new IllegalStateException("authorization failed"));

            assertThat(capture.appender().list).hasSize(2);
            assertThat(capture.appender().list.get(0).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.EVENT_CATEGORY, "http_client")
                    .containsEntry(RuntimeLogFields.EVENT_ACTION, "http_client_slow")
                    .containsEntry("peer.service", "oss")
                    .containsEntry("http.request.method", "GET")
                    .containsEntry("url.path", "/api/oss/objects/{id}")
                    .containsEntry("http.response.status_code", "200")
                    .containsEntry("duration.ms", "301")
                    .containsEntry("threshold.ms", "300");
            assertThat(capture.appender().list.get(1).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.EVENT_CATEGORY, "http_client")
                    .containsEntry(RuntimeLogFields.EVENT_ACTION, "http_client_error")
                    .containsEntry("http.response.status_code", "503")
                    .containsEntry("error.type", IllegalStateException.class.getName());
            assertThat(capture.appender().list.get(0).getFormattedMessage())
                    .doesNotContain("token=secret")
                    .doesNotContain("/api/oss/objects/1");
        }
    }

    @Test
    void masksOssPublicFileObjectKeysInHttpClientPath() {
        try (RuntimeLogTestSupport.Capture capture = RuntimeLogTestSupport.capture("test.http-client-runtime-files")) {
            RuntimeLoggingProperties properties = new RuntimeLoggingProperties();
            properties.getHttpClient().setSlowRequestThresholdMs(0);
            HttpClientRuntimeLogger logger = new HttpClientRuntimeLogger(capture.writer(), properties);

            logger.logSlowRequest("oss", "GET", "http://community-oss:18090/files/user-42/avatar/private-name.png", 200, 1);

            assertThat(capture.appender().list).hasSize(1);
            assertThat(capture.appender().list.get(0).getMDCPropertyMap())
                    .containsEntry("url.path", "/files/{objectKey}");
            assertThat(capture.appender().list.get(0).getFormattedMessage())
                    .doesNotContain("user-42")
                    .doesNotContain("private-name.png");
        }
    }
}
