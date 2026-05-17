package com.nowcoder.community.im.gateway.session;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.im.gateway.CommunityImGatewayApplication;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityImGatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ImSessionApiIntegrationTest {

    private static final String SECRET = "im-gateway-session-test-secret-please-change-123456";

    @Autowired
    WebTestClient webTestClient;

    @Autowired
    MeterRegistry meterRegistry;

    @LocalServerPort
    int localPort;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.hmac-secret", () -> SECRET);
        registry.add("security.jwt.issuer", () -> "community-auth");
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("spring.cloud.nacos.config.enabled", () -> "false");
        registry.add("im.gateway.ws.path", () -> "/custom/ws/im");
        registry.add("im.gateway.public-ws-url", () -> "ws://localhost:12880/custom/ws/im");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].uri",
                () -> "http://127.0.0.1:18081");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].metadata.workerId",
                () -> "worker-a");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].metadata.wsPath",
                () -> "/internal/ws/im");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].metadata.wsPort",
                () -> "18081");
    }

    @Test
    void shouldReturnConfiguredWsUrlAndTicket() {
        double openedBefore = counterValue("community.im.gateway.session.opened");

        webTestClient.post()
                .uri("/api/im/sessions")
                .header("Authorization", "Bearer " + accessToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.workerId").doesNotExist()
                .jsonPath("$.data.wsUrl").isEqualTo("ws://localhost:12880/custom/ws/im")
                .jsonPath("$.data.ticket").isNotEmpty();

        assertThat(counterValue("community.im.gateway.session.opened")).isEqualTo(openedBefore + 1.0);
    }

    @Test
    void shouldRejectMissingBearerToken() {
        double failedBefore = counterValue("community.im.gateway.session.failed", "reason", "invalid_token");

        webTestClient.post()
                .uri("/api/im/sessions")
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(counterValue("community.im.gateway.session.failed", "reason", "invalid_token"))
                .isEqualTo(failedBefore + 1.0);
    }

    @Test
    void shouldPermitConfiguredWsPathThroughSecurity() {
        webTestClient.get()
                .uri("/custom/ws/im")
                .exchange()
                .expectStatus().isNotFound();
    }

    private static String accessToken() {
        JwtProperties properties = new JwtProperties();
        properties.setHmacSecret(SECRET);
        properties.setIssuer("community-auth");
        JwtEncoder encoder = JwtCodecs.jwtEncoder(properties);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("community-auth")
                .subject("00000000-0000-7000-8000-000000000123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private double counterValue(String name, String... tags) {
        Counter counter = meterRegistry.find(name).tags(tags).counter();
        return counter == null ? 0.0 : counter.count();
    }
}
