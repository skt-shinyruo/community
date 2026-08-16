package com.nowcoder.community.gateway.security;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.gateway.CommunityGatewayApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@AutoConfigureWebTestClient
@SpringBootTest(
        classes = CommunityGatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class GatewayDefaultSecurityIntegrationTest {

    private static final String METRICS_USERNAME = "prometheus";
    private static final String METRICS_PASSWORD = "gateway-metrics-pass-please-change";
    private static final String LEGACY_HMAC_SECRET = "gateway-legacy-hmac-secret-for-boundary-test";
    private static final KeyPair ACCESS_KEY_PAIR = rsaKeyPair();
    private static volatile DisposableServer httpUpstream;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        String routes = "spring.cloud.gateway.server.webflux.routes";
        registry.add(routes + "[0].id", () -> "bootstrap-api");
        registry.add(routes + "[0].uri", () -> "lb://community-app");
        registry.add(routes + "[0].predicates[0]", () -> "Path=/api,/api/**");
        registry.add("spring.cloud.discovery.client.simple.instances.community-app[0].uri",
                GatewayDefaultSecurityIntegrationTest::httpUpstreamBaseUrl);
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("spring.cloud.nacos.config.enabled", () -> "false");
        registry.add("management.health.redis.enabled", () -> "false");
        registry.add("security.jwt.access-public-key", GatewayDefaultSecurityIntegrationTest::accessPublicKey);
        registry.add("community.metrics.basic-auth.username", () -> METRICS_USERNAME);
        registry.add("community.metrics.basic-auth.password", () -> METRICS_PASSWORD);
    }

    @AfterAll
    static void tearDown() {
        if (httpUpstream != null) {
            httpUpstream.disposeNow();
            httpUpstream = null;
        }
    }

    @Test
    void shouldNotRequireGatewayManagedAuthForApiProxyRoutes() {
        webTestClient.get()
                .uri("/api/posts")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.upstream").isEqualTo("bootstrap");
    }

    @Test
    void shouldNotExposeLegacyWorkerWebSocketPath() {
        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
        URI uri = URI.create("ws://127.0.0.1:" + port + "/ws/im/workers/worker-a");

        StepVerifier.create(client.execute(uri, session -> Mono.empty()))
                .expectErrorSatisfies(error -> assertThat(error).hasMessageContaining("404 Not Found"))
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void shouldReturnNotFoundForLegacyWorkerHttpPath() {
        webTestClient.get()
                .uri("/ws/im/workers/worker-a")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldRequireBasicAuthForPrometheusEndpoint() {
        webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldKeepHealthEndpointPublic() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldUseSharedReactiveInfrastructureBeans() {
        assertThat(applicationContext.getBean("traceIdWebFilter"))
                .isInstanceOf(com.nowcoder.community.common.webflux.TraceIdWebFilter.class);
        assertThat(applicationContext.getBean(ServerAuthenticationEntryPoint.class))
                .isInstanceOf(com.nowcoder.community.common.webflux.SecurityExceptionHandler.class);
        assertThat(applicationContext.getBean(ServerAccessDeniedHandler.class))
                .isInstanceOf(com.nowcoder.community.common.webflux.SecurityExceptionHandler.class);
        assertThat(applicationContext.getBeansOfType(JwtDecoder.class)).hasSize(1);
        assertThat(applicationContext.containsBean("gatewayJwtDecoderConfig")).isFalse();
    }

    @Test
    void shouldKeepGatewayOnTheAccessTokenVerificationTrustBoundary() {
        JwtProperties boundProperties = applicationContext.getBean(JwtProperties.class);
        JwtDecoder decoder = applicationContext.getBean(JwtDecoder.class);

        Jwt decoded = decoder.decode(accessToken(JwtCodecs.ACCESS_TOKEN_TYPE, "community-api"));

        assertThat(decoded.getHeaders())
                .containsEntry("alg", "RS256")
                .containsEntry("typ", JwtCodecs.ACCESS_TOKEN_TYPE);
        assertThat(decoded.getAudience()).containsExactly("community-api");
        assertThat(boundProperties.getAccessPrivateKey()).isNull();
        assertThat(boundProperties.getServiceHmacSecret()).isNull();
        assertThatThrownBy(() -> decoder.decode(accessToken(JwtCodecs.SERVICE_TOKEN_TYPE, "community-api")))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(accessToken(JwtCodecs.ACCESS_TOKEN_TYPE, "community-oss")))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(legacyHmacAccessToken()))
                .isInstanceOf(JwtException.class);
    }

    private static synchronized String httpUpstreamBaseUrl() {
        if (httpUpstream == null) {
            httpUpstream = HttpServer.create()
                    .host("127.0.0.1")
                    .port(0)
                    .route(routes -> routes.get("/api/posts", (req, res) ->
                            res.sendString(Mono.just("{\"upstream\":\"bootstrap\"}"))))
                    .bindNow(Duration.ofSeconds(5));
        }
        return "http://127.0.0.1:" + httpUpstream.port();
    }

    private static String accessToken(String type, String audience) {
        JwtProperties properties = accessSigningProperties();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .type(type)
                .build();
        return JwtCodecs.accessTokenEncoder(properties)
                .encode(JwtEncoderParameters.from(header, tokenClaims(audience)))
                .getTokenValue();
    }

    private static String legacyHmacAccessToken() {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("community-auth");
        properties.setServiceHmacSecret(LEGACY_HMAC_SECRET);
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type(JwtCodecs.ACCESS_TOKEN_TYPE)
                .build();
        return JwtCodecs.serviceTokenEncoder(properties)
                .encode(JwtEncoderParameters.from(header, tokenClaims("community-api")))
                .getTokenValue();
    }

    private static JwtClaimsSet tokenClaims(String audience) {
        Instant now = Instant.now();
        return JwtClaimsSet.builder()
                .subject("123")
                .issuer("community-auth")
                .audience(List.of(audience))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }

    private static JwtProperties accessSigningProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setAccessPublicKey(accessPublicKey());
        properties.setAccessPrivateKey(Base64.getEncoder().encodeToString(ACCESS_KEY_PAIR.getPrivate().getEncoded()));
        properties.setIssuer("community-auth");
        properties.setAccessTokenAudience("community-api");
        return properties;
    }

    private static String accessPublicKey() {
        return Base64.getEncoder().encodeToString(ACCESS_KEY_PAIR.getPublic().getEncoded());
    }

    private static KeyPair rsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

}
