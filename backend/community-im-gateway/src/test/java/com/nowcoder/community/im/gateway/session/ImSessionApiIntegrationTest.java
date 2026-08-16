package com.nowcoder.community.im.gateway.session;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.im.gateway.CommunityImGatewayApplication;
import com.nowcoder.community.im.gateway.TestJwtKeys;
import com.nowcoder.community.im.gateway.security.AccessTokenFreshnessVerifier;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {CommunityImGatewayApplication.class, ImSessionApiIntegrationTest.FreshnessTestConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureWebTestClient
class ImSessionApiIntegrationTest {

    private static final String SECRET = "im-gateway-session-test-secret-please-change-123456";
    private static final String TICKET_SECRET = "im-gateway-dedicated-ticket-test-secret-1234567890";

    @Autowired
    WebTestClient webTestClient;

    @Autowired
    MeterRegistry meterRegistry;

    @Autowired
    StubAccessTokenFreshnessVerifier accessTokenFreshnessVerifier;

    @LocalServerPort
    int localPort;

    @BeforeEach
    void resetFreshness() {
        accessTokenFreshnessVerifier.reset();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.access-public-key", TestJwtKeys::publicKey);
        registry.add("security.jwt.issuer", () -> "community-auth");
        registry.add("im.session-ticket.hmac-secret", () -> TICKET_SECRET);
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("spring.cloud.nacos.config.enabled", () -> "false");
        registry.add("im.gateway.ws.path", () -> "/custom/ws/im");
        registry.add("im.gateway.public-ws-url", () -> "ws://localhost:12880/custom/ws/im");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].uri",
                () -> "http://127.0.0.1:18081");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].metadata.workerId",
                () -> "worker-a");
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
    void shouldRejectNewlyIssuedSessionTicketAsAccessBearer() {
        AtomicReference<String> issuedTicket = new AtomicReference<>();
        webTestClient.post()
                .uri("/api/im/sessions")
                .header("Authorization", "Bearer " + accessToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.ticket")
                .value(value -> issuedTicket.set((String) value));

        assertThat(issuedTicket.get()).isNotBlank();
        webTestClient.post()
                .uri("/api/im/sessions")
                .header("Authorization", "Bearer " + issuedTicket.get())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectAccessTokenWithoutSecurityVersionBeforeOwnerLookup() {
        webTestClient.post()
                .uri("/api/im/sessions")
                .header("Authorization", "Bearer " + accessTokenWithoutSecurityVersion())
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(accessTokenFreshnessVerifier.invocations()).isZero();
    }

    @Test
    void shouldRejectStaleAndDisabledTokensAndFailClosedWhenOwnerIsUnavailable() {
        assertFreshnessDecision(AccessTokenFreshnessVerifier.Decision.STALE, 401);
        assertFreshnessDecision(AccessTokenFreshnessVerifier.Decision.DENIED, 403);
        assertFreshnessDecision(AccessTokenFreshnessVerifier.Decision.UNAVAILABLE, 503);
    }

    @Test
    void shouldPermitConfiguredWsPathThroughSecurity() {
        webTestClient.get()
                .uri("/custom/ws/im")
                .exchange()
                .expectStatus().isNotFound();
    }

    private static String accessToken() {
        return accessToken(true);
    }

    private static String accessTokenWithoutSecurityVersion() {
        return accessToken(false);
    }

    private static String accessToken(boolean includeSecurityVersion) {
        JwtProperties properties = TestJwtKeys.accessProperties();
        JwtEncoder encoder = JwtCodecs.accessTokenEncoder(properties);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("community-auth")
                .subject("00000000-0000-7000-8000-000000000123")
                .audience(java.util.List.of("community-api"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (includeSecurityVersion) {
            claims.claim("security_version", 7L);
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .type(JwtCodecs.ACCESS_TOKEN_TYPE)
                .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build()))
                .getTokenValue();
    }

    private void assertFreshnessDecision(AccessTokenFreshnessVerifier.Decision decision, int expectedStatus) {
        accessTokenFreshnessVerifier.decision(decision);

        webTestClient.post()
                .uri("/api/im/sessions")
                .header("Authorization", "Bearer " + accessToken())
                .exchange()
                .expectStatus().isEqualTo(expectedStatus);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FreshnessTestConfig {

        @Bean
        @Primary
        StubAccessTokenFreshnessVerifier stubAccessTokenFreshnessVerifier() {
            return new StubAccessTokenFreshnessVerifier();
        }
    }

    static final class StubAccessTokenFreshnessVerifier implements AccessTokenFreshnessVerifier {

        private final AtomicReference<Decision> decision = new AtomicReference<>(Decision.FRESH);
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public reactor.core.publisher.Mono<Decision> verify(String accessToken) {
            invocations.incrementAndGet();
            return reactor.core.publisher.Mono.just(decision.get());
        }

        void decision(Decision next) {
            decision.set(next);
        }

        int invocations() {
            return invocations.get();
        }

        void reset() {
            decision.set(Decision.FRESH);
            invocations.set(0);
        }
    }

    private double counterValue(String name, String... tags) {
        Counter counter = meterRegistry.find(name).tags(tags).counter();
        return counter == null ? 0.0 : counter.count();
    }
}
