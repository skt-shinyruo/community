package com.nowcoder.community.gateway.edge;

import com.nowcoder.community.gateway.CommunityGatewayApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the native per-route RequestRateLimiter wiring: key selection (principal vs client IP,
 * with X-Forwarded-For resolved by the gateway Netty customizer for trusted proxy peers),
 * 429 on limit exhaustion, and fail-closed behavior when the limiter backend errors.
 */
@AutoConfigureWebTestClient
@SpringBootTest(
        classes = {
                CommunityGatewayApplication.class,
                RateLimitSecurityOrderIntegrationTest.ProbeConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class RateLimitSecurityOrderIntegrationTest {

    private static final String PROBE_PATH = "/api/rate-limit-order-probe";
    private static final BlockingQueue<String> RATE_LIMIT_KEYS = new LinkedBlockingQueue<>();
    private static final AtomicReference<Mode> MODE = new AtomicReference<>(Mode.ALLOW);

    private static volatile DisposableServer downstreamServer;

    private enum Mode {
        ALLOW,
        DENY,
        ERROR
    }

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        String routes = "spring.cloud.gateway.server.webflux.routes";
        registry.add(routes + "[0].id", () -> "rate-limit-probe");
        registry.add(routes + "[0].uri", () -> "lb://community-app");
        registry.add(routes + "[0].predicates[0]", () -> "Path=" + PROBE_PATH);
        registry.add(routes + "[0].filters[0].name", () -> "RequestRateLimiter");
        registry.add(routes + "[0].filters[0].args[key-resolver]", () -> "#{@gatewayRateLimitKeyResolver}");
        registry.add("spring.cloud.discovery.client.simple.instances.community-app[0].uri",
                RateLimitSecurityOrderIntegrationTest::downstreamBaseUrl);
        // Loopback stands in for the NGINX peer: trusted, so inbound X-Forwarded-For is resolved.
        registry.add("spring.cloud.gateway.server.webflux.trusted-proxies", () -> "127\\.0\\.0\\.1|::1");
        registry.add("spring.cloud.gateway.server.webflux.httpserver.customizer-enabled", () -> "true");
        registry.add("spring.cloud.gateway.discovery.locator.enabled", () -> "false");
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("spring.cloud.nacos.config.enabled", () -> "false");
    }

    @AfterAll
    static void stopServer() {
        if (downstreamServer != null) {
            downstreamServer.disposeNow();
            downstreamServer = null;
        }
    }

    @BeforeEach
    void resetState() {
        RATE_LIMIT_KEYS.clear();
        MODE.set(Mode.ALLOW);
    }

    @Test
    void shouldUseAuthenticatedPrincipalAfterSecurityFilterChainRuns() throws Exception {
        webTestClient.get()
                .uri(PROBE_PATH)
                .headers(headers -> headers.setBasicAuth("alice", "password"))
                .header("X-Forwarded-For", "203.0.113.7")
                .exchange()
                .expectStatus().isOk();

        assertThat(RATE_LIMIT_KEYS.poll(5, TimeUnit.SECONDS)).isEqualTo("principal:alice");
        assertThat(RATE_LIMIT_KEYS).isEmpty();
    }

    @Test
    void shouldKeyAnonymousRequestsByClientIp() throws Exception {
        webTestClient.get()
                .uri(PROBE_PATH)
                .exchange()
                .expectStatus().isOk();

        assertThat(RATE_LIMIT_KEYS.poll(5, TimeUnit.SECONDS)).isEqualTo("ip:127.0.0.1");
    }

    @Test
    void shouldResolveClientIpFromForwardedHeaderForTrustedProxyPeer() throws Exception {
        // Production ingress (NGINX) overwrites X-Forwarded-For with the real client IP.
        webTestClient.get()
                .uri(PROBE_PATH)
                .header("X-Forwarded-For", "203.0.113.7")
                .exchange()
                .expectStatus().isOk();

        assertThat(RATE_LIMIT_KEYS.poll(5, TimeUnit.SECONDS)).isEqualTo("ip:203.0.113.7");
    }

    @Test
    void shouldReturn429WhenLimitExceeded() throws Exception {
        MODE.set(Mode.DENY);

        webTestClient.get()
                .uri(PROBE_PATH)
                .exchange()
                .expectStatus().isEqualTo(429);

        assertThat(RATE_LIMIT_KEYS.poll(5, TimeUnit.SECONDS)).isEqualTo("ip:127.0.0.1");
    }

    @Test
    void shouldFailClosedWhenRateLimiterBackendErrors() throws Exception {
        MODE.set(Mode.ERROR);

        // Fail-closed is preserved; the native filter propagates the backend error (5xx)
        // instead of the legacy fixed-window filter's synthetic 429.
        webTestClient.get()
                .uri(PROBE_PATH)
                .exchange()
                .expectStatus().is5xxServerError();
    }

    private static synchronized String downstreamBaseUrl() {
        if (downstreamServer == null) {
            downstreamServer = HttpServer.create()
                    .host("127.0.0.1")
                    .port(0)
                    .handle((request, response) -> response
                            .header("Content-Type", "application/json")
                            .sendString(Mono.just("{\"ok\":true}"))
                            .then())
                    .bindNow(Duration.ofSeconds(5));
        }
        return "http://127.0.0.1:" + downstreamServer.port();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfig {

        @Bean
        @Primary
        RateLimiter<Object> recordingRateLimiter() {
            return new RateLimiter<>() {
                @Override
                public Mono<Response> isAllowed(String routeId, String key) {
                    RATE_LIMIT_KEYS.add(key);
                    return switch (MODE.get()) {
                        case ALLOW -> Mono.just(new Response(true, Map.of()));
                        case DENY -> Mono.just(new Response(false, Map.of()));
                        case ERROR -> Mono.error(new IllegalStateException("redis unavailable"));
                    };
                }

                @Override
                public Class<Object> getConfigClass() {
                    return Object.class;
                }

                @Override
                public Object newConfig() {
                    return new Object();
                }

                @Override
                public Map<String, Object> getConfig() {
                    return Map.of();
                }
            };
        }

        @Bean
        MapReactiveUserDetailsService testUsers() {
            return new MapReactiveUserDetailsService(User.withUsername("alice")
                    .password("{noop}password")
                    .roles("USER")
                    .build());
        }

        @Bean
        @Order(0)
        SecurityWebFilterChain authenticatedProbeSecurityWebFilterChain(ServerHttpSecurity http) {
            return http
                    .securityMatcher(ServerWebExchangeMatchers.pathMatchers(PROBE_PATH))
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .httpBasic(Customizer.withDefaults())
                    .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                    .logout(ServerHttpSecurity.LogoutSpec::disable)
                    // Same posture as production (permitAll): credentials still authenticate,
                    // so a presented principal must win over the client-IP key.
                    .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                    .build();
        }
    }
}
