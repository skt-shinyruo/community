package com.nowcoder.community.infra.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = ActuatorSecurityReactiveTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.web-application-type=reactive",
                "security.jwt.hmac-secret=01234567890123456789012345678901",
                "community.metrics.basic-auth.username=prometheus",
                "community.metrics.basic-auth.password=test-prometheus-pass-please-change",
                "management.endpoint.prometheus.enabled=true",
                "management.metrics.export.prometheus.enabled=true",
                "management.prometheus.metrics.export.enabled=true",
                "management.endpoints.web.exposure.include=health,info,prometheus,env"
        }
)
@AutoConfigureWebTestClient
class ActuatorSecurityReactiveTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void uses_reactive_security_chain() {
        assertThat(applicationContext.containsBean("actuatorSecurityWebFilterChain")).isTrue();
        assertThat(applicationContext.containsBean("actuatorSecurityFilterChain")).isFalse();
    }

    @Test
    void health_and_info_are_public() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri("/actuator/info")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void prometheus_requires_prometheus_role() {
        webTestClient.get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.get().uri("/actuator/prometheus")
                .headers(headers -> headers.setBasicAuth("prometheus", "test-prometheus-pass-please-change"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void other_actuator_endpoints_are_deny_all() {
        webTestClient.get().uri("/actuator/env")
                .headers(headers -> headers.setBasicAuth("prometheus", "test-prometheus-pass-please-change"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @SpringBootApplication
    static class TestApp {
    }
}
