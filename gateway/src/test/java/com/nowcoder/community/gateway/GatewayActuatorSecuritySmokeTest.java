package com.nowcoder.community.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 最小冒烟：验证 gateway 引入 infra-security-starter 后，actuator 安全策略与其他服务保持一致。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.endpoint.prometheus.enabled=true",
                "management.metrics.export.prometheus.enabled=true",
                "management.prometheus.metrics.export.enabled=true",
                "management.endpoints.web.exposure.include=health,info,prometheus"
        }
)
class GatewayActuatorSecuritySmokeTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void actuatorHealth_shouldBePublic() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void actuatorPrometheus_shouldRequireBasicAuth() {
        webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus()
                .isUnauthorized();

        webTestClient.get()
                .uri("/actuator/prometheus")
                .headers(headers -> headers.setBasicAuth("prometheus", "test-prometheus-pass-please-change"))
                .exchange()
                .expectStatus()
                .isOk();
    }
}
