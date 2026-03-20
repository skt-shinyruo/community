package com.nowcoder.community.im.realtime.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ImRealtimeSecurityIntegrationTest {

    @LocalServerPort
    private int port;

    private WebTestClient client() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(5))
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(256 * 1024))
                        .build())
                .build();
    }

    @Test
    void unknownEndpoint_shouldBeDenied_byDefault() {
        client()
                .get()
                .uri("/__should_be_denied__")
                .exchange()
                .expectStatus()
                .value(code -> assertThat(code).isIn(401, 403));
    }

    @Test
    void actuatorHealth_shouldBePublic() {
        client()
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk();
    }
}

