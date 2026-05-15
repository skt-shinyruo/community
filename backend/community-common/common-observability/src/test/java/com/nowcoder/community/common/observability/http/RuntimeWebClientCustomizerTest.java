package com.nowcoder.community.common.observability.http;

import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogTestSupport;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeWebClientCustomizerTest {

    @Test
    void logsSlowAndErrorRequestsWithoutQueryOrBody() {
        try (RuntimeLogTestSupport.Capture capture = RuntimeLogTestSupport.capture("test.web-client-runtime")) {
            RuntimeLoggingProperties properties = new RuntimeLoggingProperties();
            properties.getHttpClient().setSlowRequestThresholdMs(0);
            HttpClientRuntimeLogger logger = new HttpClientRuntimeLogger(capture.writer(), properties);
            WebClientCustomizer customizer = new RuntimeWebClientCustomizer(logger);
            WebClient.Builder builder = WebClient.builder()
                    .exchangeFunction(request -> {
                        if (request.url().getPath().endsWith("/8")) {
                            return Mono.just(ClientResponse.create(HttpStatus.BAD_GATEWAY).build());
                        }
                        return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                    });
            customizer.customize(builder);
            WebClient webClient = builder.build();

            webClient.get()
                    .uri("https://profiles.example/api/users/7?token=secret")
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            assertThatThrownBy(() -> webClient.get()
                    .uri("https://profiles.example/api/users/8?token=secret")
                    .retrieve()
                    .toBodilessEntity()
                    .block())
                    .isInstanceOf(WebClientResponseException.class);

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
