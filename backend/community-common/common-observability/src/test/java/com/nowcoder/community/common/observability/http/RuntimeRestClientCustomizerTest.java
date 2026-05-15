package com.nowcoder.community.common.observability.http;

import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogTestSupport;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RuntimeRestClientCustomizerTest {

    @Test
    void logsSlowAndErrorRequestsWithoutQueryOrBody() {
        try (RuntimeLogTestSupport.Capture capture = RuntimeLogTestSupport.capture("test.rest-client-runtime")) {
            RuntimeLoggingProperties properties = new RuntimeLoggingProperties();
            properties.getHttpClient().setSlowRequestThresholdMs(0);
            HttpClientRuntimeLogger logger = new HttpClientRuntimeLogger(capture.writer(), properties);
            RestClient.Builder builder = RestClient.builder();
            new RuntimeRestClientCustomizer(logger).customize(builder);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("https://profiles.example/api/users/7?token=secret"))
                    .andRespond(withSuccess());
            server.expect(requestTo("https://profiles.example/api/users/8?token=secret"))
                    .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
            RestClient restClient = builder.build();

            restClient.get()
                    .uri("https://profiles.example/api/users/7?token=secret")
                    .retrieve()
                    .toBodilessEntity();
            assertThatThrownBy(() -> restClient.get()
                    .uri("https://profiles.example/api/users/8?token=secret")
                    .retrieve()
                    .toBodilessEntity())
                    .isInstanceOf(RestClientResponseException.class);

            assertThat(capture.appender().list)
                    .anySatisfy(event -> assertThat(event.getMDCPropertyMap())
                            .containsEntry(RuntimeLogFields.EVENT_ACTION, "http_client_slow")
                            .containsEntry("peer.service", "profiles.example")
                            .containsEntry("url.path", "/api/users/{id}")
                            .doesNotContainValue("secret"))
                    .anySatisfy(event -> assertThat(event.getMDCPropertyMap())
                            .containsEntry(RuntimeLogFields.EVENT_ACTION, "http_client_error")
                            .containsEntry("http.response.status_code", "502")
                            .containsEntry("url.path", "/api/users/{id}")
                            .doesNotContainValue("secret"));
        }
    }
}
