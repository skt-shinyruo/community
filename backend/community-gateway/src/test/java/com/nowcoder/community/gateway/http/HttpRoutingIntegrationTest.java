package com.nowcoder.community.gateway.http;

import com.nowcoder.community.gateway.CommunityGatewayApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;
import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {
                CommunityGatewayApplication.class,
                HttpRoutingIntegrationTest.PermitAllSecurityConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class HttpRoutingIntegrationTest {

    private static final BlockingQueue<RequestCapture> BOOTSTRAP_CAPTURES = new LinkedBlockingQueue<>();
    private static final BlockingQueue<RequestCapture> IM_CAPTURES = new LinkedBlockingQueue<>();
    private static final String TRACEPARENT_HEADER = "traceparent";

    private static volatile DisposableServer bootstrapServer;
    private static volatile DisposableServer imServer;

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.http.routes[0].id", () -> "bootstrap-api");
        registry.add("gateway.http.routes[0].path-prefix", () -> "/api");
        registry.add("gateway.http.routes[0].uri", HttpRoutingIntegrationTest::bootstrapBaseUrl);
        registry.add("gateway.http.routes[1].id", () -> "im-core");
        registry.add("gateway.http.routes[1].path-prefix", () -> "/api/im");
        registry.add("gateway.http.routes[1].uri", HttpRoutingIntegrationTest::imBaseUrl);
        registry.add("gateway.http.routes[2].id", () -> "bootstrap-files");
        registry.add("gateway.http.routes[2].path-prefix", () -> "/files");
        registry.add("gateway.http.routes[2].uri", HttpRoutingIntegrationTest::bootstrapBaseUrl);
        registry.add("gateway.cors.allowed-origins[0]", () -> "http://localhost:12881");
        registry.add("gateway.cors.allowed-origins[1]", () -> "http://127.0.0.1:12881");
    }

    @AfterAll
    static void stopServers() {
        if (bootstrapServer != null) {
            bootstrapServer.disposeNow();
            bootstrapServer = null;
        }
        if (imServer != null) {
            imServer.disposeNow();
            imServer = null;
        }
    }

    @Test
    void shouldProxyApiRequestsToBootstrapAndForwardMethodQueryBodyAndHeaders() throws Exception {
        BOOTSTRAP_CAPTURES.clear();

        webTestClient.post()
                .uri("/api/posts?draft=true&limit=10")
                .header("Authorization", "Bearer test-token")
                .header("X-Trace-Id", "ABCDEFABCDEFABCDEFABCDEFABCDEFAB")
                .bodyValue("{\"title\":\"Gateway\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Trace-Id", "abcdefabcdefabcdefabcdefabcdefab")
                .expectBody()
                .jsonPath("$.upstream").isEqualTo("bootstrap");

        RequestCapture capture = BOOTSTRAP_CAPTURES.poll(5, TimeUnit.SECONDS);
        assertThat(capture).isNotNull();
        assertThat(capture.method()).isEqualTo("POST");
        assertThat(capture.path()).isEqualTo("/api/posts");
        assertThat(capture.query()).isEqualTo("draft=true&limit=10");
        assertThat(capture.body()).isEqualTo("{\"title\":\"Gateway\"}");
        assertThat(capture.authorization()).isEqualTo("Bearer test-token");
        assertThat(capture.traceId()).isEqualTo("abcdefabcdefabcdefabcdefabcdefab");
        assertThat(capture.traceparent()).startsWith("00-abcdefabcdefabcdefabcdefabcdefab-");
    }

    @Test
    void shouldPreferTraceparentTraceIdWhenProxying() throws Exception {
        BOOTSTRAP_CAPTURES.clear();

        webTestClient.get()
                .uri("/api/posts")
                .header("X-Trace-Id", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .header(TRACEPARENT_HEADER, traceparent("4bf92f3577b34da6a3ce929d0e0e4736"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Trace-Id", "4bf92f3577b34da6a3ce929d0e0e4736");

        RequestCapture capture = BOOTSTRAP_CAPTURES.poll(5, TimeUnit.SECONDS);
        assertThat(capture).isNotNull();
        assertThat(capture.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(capture.traceparent()).startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-");
    }

    @Test
    void shouldPreferLongestPrefixForImRoutes() throws Exception {
        BOOTSTRAP_CAPTURES.clear();
        IM_CAPTURES.clear();
        String traceId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

        webTestClient.get()
                .uri("/api/im/conversations?unreadOnly=true")
                .header("Authorization", "Bearer im-token")
                .header("X-Trace-Id", traceId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.upstream").isEqualTo("im-core");

        RequestCapture capture = IM_CAPTURES.poll(5, TimeUnit.SECONDS);
        assertThat(capture).isNotNull();
        assertThat(capture.path()).isEqualTo("/api/im/conversations");
        assertThat(capture.query()).isEqualTo("unreadOnly=true");
        assertThat(capture.authorization()).isEqualTo("Bearer im-token");
        assertThat(capture.traceId()).isEqualTo(traceId);
        assertThat(BOOTSTRAP_CAPTURES).isEmpty();
    }

    @Test
    void shouldNotExposeInternalPaths() {
        BOOTSTRAP_CAPTURES.clear();
        IM_CAPTURES.clear();

        webTestClient.get()
                .uri("/internal/health")
                .exchange()
                .expectStatus().isNotFound();

        assertThat(BOOTSTRAP_CAPTURES).isEmpty();
        assertThat(IM_CAPTURES).isEmpty();
    }

    @Test
    void shouldHandleGatewayCorsPreflightWithoutLeakingToUpstream() {
        BOOTSTRAP_CAPTURES.clear();
        IM_CAPTURES.clear();

        webTestClient.options()
                .uri("/api/posts")
                .header("Origin", "http://localhost:12881")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization,Content-Type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:12881")
                .expectHeader().valueMatches("Vary", ".*Origin.*")
                .expectHeader().valueMatches("Access-Control-Allow-Methods", ".*POST.*");

        assertThat(BOOTSTRAP_CAPTURES).isEmpty();
        assertThat(IM_CAPTURES).isEmpty();
    }

    @Test
    void shouldAllow127LoopbackGatewayCorsPreflightWithoutLeakingToUpstream() {
        BOOTSTRAP_CAPTURES.clear();
        IM_CAPTURES.clear();

        webTestClient.options()
                .uri("/api/posts")
                .header("Origin", "http://127.0.0.1:12881")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization,Content-Type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://127.0.0.1:12881")
                .expectHeader().valueMatches("Vary", ".*Origin.*")
                .expectHeader().valueMatches("Access-Control-Allow-Methods", ".*POST.*");

        assertThat(BOOTSTRAP_CAPTURES).isEmpty();
        assertThat(IM_CAPTURES).isEmpty();
    }

    private static synchronized String bootstrapBaseUrl() {
        if (bootstrapServer == null) {
            bootstrapServer = startServer("bootstrap", BOOTSTRAP_CAPTURES);
        }
        return "http://127.0.0.1:" + bootstrapServer.port();
    }

    private static synchronized String imBaseUrl() {
        if (imServer == null) {
            imServer = startServer("im-core", IM_CAPTURES);
        }
        return "http://127.0.0.1:" + imServer.port();
    }

    private static DisposableServer startServer(String upstreamName, BlockingQueue<RequestCapture> captures) {
        return HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> request.receive()
                        .aggregate()
                        .asString()
                        .defaultIfEmpty("")
                        .flatMap(body -> {
                            captures.add(new RequestCapture(
                                    request.method().name(),
                                    URI.create(request.uri()).getPath(),
                                    URI.create(request.uri()).getRawQuery(),
                                    body,
                                    request.requestHeaders().get("Authorization"),
                                    request.requestHeaders().get("X-Trace-Id"),
                                    request.requestHeaders().get(TRACEPARENT_HEADER)
                            ));
                            return response.header("Content-Type", "application/json")
                                    .sendString(Mono.just("{\"upstream\":\"" + upstreamName + "\"}"))
                                    .then();
                        }))
                .bindNow(Duration.ofSeconds(5));
    }

    private record RequestCapture(
            String method,
            String path,
            String query,
            String body,
            String authorization,
            String traceId,
            String traceparent
    ) {
    }

    private static String traceparent(String traceId) {
        return "00-" + traceId + "-00f067aa0ba902b7-01";
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebFluxSecurity
    static class PermitAllSecurityConfig {

        @Bean
        SecurityWebFilterChain testSecurityWebFilterChain(ServerHttpSecurity http) {
            return http
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                    .build();
        }
    }
}
