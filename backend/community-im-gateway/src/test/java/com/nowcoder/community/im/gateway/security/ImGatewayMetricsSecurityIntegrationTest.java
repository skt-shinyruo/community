package com.nowcoder.community.im.gateway.security;

import com.nowcoder.community.im.gateway.CommunityImGatewayApplication;
import com.nowcoder.community.im.gateway.TestJwtKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityImGatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "JM.LOG.PATH=/tmp/community-im-gateway-test-logs"
)
@AutoConfigureWebTestClient
class ImGatewayMetricsSecurityIntegrationTest {

    private static final String METRICS_USERNAME = "prometheus";
    private static final String METRICS_PASSWORD = "im-gateway-metrics-pass-please-change";

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.access-public-key", TestJwtKeys::publicKey);
        registry.add("security.jwt.issuer", () -> "community-auth");
        registry.add("im.session-ticket.hmac-secret", () -> "im-gateway-metrics-test-ticket-secret-123456");
        registry.add("community.metrics.basic-auth.username", () -> METRICS_USERNAME);
        registry.add("community.metrics.basic-auth.password", () -> METRICS_PASSWORD);
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("spring.cloud.nacos.config.enabled", () -> "false");
    }

    @Test
    void shouldRequireBasicAuthForPrometheusAndKeepHealthPublic() {
        webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.get()
                .uri("/actuator/prometheus")
                .headers(headers -> headers.setBasicAuth("wrong", "wrong-password"))
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.get()
                .uri("/actuator/prometheus")
                .headers(headers -> headers.setBasicAuth(METRICS_USERNAME, METRICS_PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("# HELP"));

        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
