package com.nowcoder.community.gateway.http;

import com.nowcoder.community.common.trace.TraceIdCodec;
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
    private static final BlockingQueue<RequestCapture> OSS_CAPTURES = new LinkedBlockingQueue<>();
    private static final String TRACEPARENT_HEADER = "traceparent";

    private static volatile DisposableServer bootstrapServer;
    private static volatile DisposableServer imServer;
    private static volatile DisposableServer ossServer;

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.http.routes[0].id", () -> "oss-api");
        registry.add("gateway.http.routes[0].path-prefix", () -> "/api/oss");
        registry.add("gateway.http.routes[0].service-id", () -> "community-oss");
        registry.add("gateway.http.routes[1].id", () -> "im-core");
        registry.add("gateway.http.routes[1].path-prefix", () -> "/api/im");
        registry.add("gateway.http.routes[1].service-id", () -> "im-core");
        registry.add("gateway.http.routes[2].id", () -> "bootstrap-api");
        registry.add("gateway.http.routes[2].path-prefix", () -> "/api");
        registry.add("gateway.http.routes[2].service-id", () -> "community-app");
        registry.add("gateway.http.routes[3].id", () -> "oss-files");
        registry.add("gateway.http.routes[3].path-prefix", () -> "/files");
        registry.add("gateway.http.routes[3].service-id", () -> "community-oss");
        registry.add("spring.cloud.discovery.client.simple.instances.community-app[0].uri", HttpRoutingIntegrationTest::bootstrapBaseUrl);
        registry.add("spring.cloud.discovery.client.simple.instances.im-core[0].uri", HttpRoutingIntegrationTest::imBaseUrl);
        registry.add("spring.cloud.discovery.client.simple.instances.community-oss[0].uri", HttpRoutingIntegrationTest::ossBaseUrl);
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("spring.cloud.nacos.config.enabled", () -> "false");
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
        if (ossServer != null) {
            ossServer.disposeNow();
            ossServer = null;
        }
    }

    @Test
    void shouldProxyApiRequestsToBootstrapAndForwardMethodQueryBodyAndHeaders() throws Exception {
        BOOTSTRAP_CAPTURES.clear();

        webTestClient.post()
                .uri("/api/posts?draft=true&limit=10")
                .header("Authorization", "Bearer test-token")
                .header(TRACEPARENT_HEADER, traceparent("abcdefabcdefabcdefabcdefabcdefab"))
                .bodyValue("{\"title\":\"Gateway\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(TRACEPARENT_HEADER, traceparent("abcdefabcdefabcdefabcdefabcdefab"))
                .expectBody()
                .jsonPath("$.upstream").isEqualTo("bootstrap");

        RequestCapture capture = BOOTSTRAP_CAPTURES.poll(5, TimeUnit.SECONDS);
        assertThat(capture).isNotNull();
        assertThat(capture.method()).isEqualTo("POST");
        assertThat(capture.path()).isEqualTo("/api/posts");
        assertThat(capture.query()).isEqualTo("draft=true&limit=10");
        assertThat(capture.body()).isEqualTo("{\"title\":\"Gateway\"}");
        assertThat(capture.authorization()).isEqualTo("Bearer test-token");
        assertThat(capture.traceparent()).isEqualTo(traceparent("abcdefabcdefabcdefabcdefabcdefab"));
        assertThat(TraceIdCodec.extractTraceIdFromTraceparent(capture.traceparent()))
                .isEqualTo("abcdefabcdefabcdefabcdefabcdefab");
    }

    @Test
    void shouldProxyOssApiRequestsToOssServiceAndPreferItOverBootstrapApiRoute() throws Exception {
        BOOTSTRAP_CAPTURES.clear();
        OSS_CAPTURES.clear();

        webTestClient.post()
                .uri("/api/oss/objects/upload-sessions")
                .bodyValue("{\"usage\":\"USER_AVATAR\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.upstream").isEqualTo("community-oss");

        RequestCapture capture = OSS_CAPTURES.poll(5, TimeUnit.SECONDS);
        assertThat(capture).isNotNull();
        assertThat(capture.method()).isEqualTo("POST");
        assertThat(capture.path()).isEqualTo("/api/oss/objects/upload-sessions");
        assertThat(capture.body()).isEqualTo("{\"usage\":\"USER_AVATAR\"}");
        assertThat(BOOTSTRAP_CAPTURES).isEmpty();
    }

    @Test
    void shouldProxyFilesRequestsToOssServiceAndPreservePathAndQuery() throws Exception {
        OSS_CAPTURES.clear();

        webTestClient.get()
                .uri("/files/avatars/9.png?variant=thumb")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.upstream").isEqualTo("community-oss");

        RequestCapture capture = OSS_CAPTURES.poll(5, TimeUnit.SECONDS);
        assertThat(capture).isNotNull();
        assertThat(capture.path()).isEqualTo("/files/avatars/9.png");
        assertThat(capture.query()).isEqualTo("variant=thumb");
    }

    @Test
    void shouldProxyTraceparentWhenPresent() throws Exception {
        BOOTSTRAP_CAPTURES.clear();

        webTestClient.get()
                .uri("/api/posts")
                .header(TRACEPARENT_HEADER, traceparent("4bf92f3577b34da6a3ce929d0e0e4736"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(TRACEPARENT_HEADER, traceparent("4bf92f3577b34da6a3ce929d0e0e4736"));

        RequestCapture capture = BOOTSTRAP_CAPTURES.poll(5, TimeUnit.SECONDS);
        assertThat(capture).isNotNull();
        assertThat(capture.traceparent()).isEqualTo(traceparent("4bf92f3577b34da6a3ce929d0e0e4736"));
    }

    @Test
    void shouldPreferLongestPrefixForImRoutes() throws Exception {
        BOOTSTRAP_CAPTURES.clear();
        IM_CAPTURES.clear();
        String traceId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

        webTestClient.get()
                .uri("/api/im/conversations?unreadOnly=true")
                .header("Authorization", "Bearer im-token")
                .header(TRACEPARENT_HEADER, traceparent(traceId))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.upstream").isEqualTo("im-core");

        RequestCapture capture = IM_CAPTURES.poll(5, TimeUnit.SECONDS);
        assertThat(capture).isNotNull();
        assertThat(capture.path()).isEqualTo("/api/im/conversations");
        assertThat(capture.query()).isEqualTo("unreadOnly=true");
        assertThat(capture.authorization()).isEqualTo("Bearer im-token");
        assertThat(capture.traceparent()).isEqualTo(traceparent(traceId));
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

    @Test
    void shouldKeepSingleCorsHeaderWhenUpstreamAlsoReturnsCorsHeaders() {
        BOOTSTRAP_CAPTURES.clear();

        var result = webTestClient.get()
                .uri("/api/categories?upstreamCors=true")
                .header("Origin", "http://localhost:12881")
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class);

        assertThat(result.getResponseHeaders().get("Access-Control-Allow-Origin"))
                .containsExactly("http://localhost:12881");
        assertThat(result.getResponseHeaders().get("Access-Control-Allow-Credentials"))
                .containsExactly("true");
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

    private static synchronized String ossBaseUrl() {
        if (ossServer == null) {
            ossServer = startServer("community-oss", OSS_CAPTURES);
        }
        return "http://127.0.0.1:" + ossServer.port();
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
                            URI uri = URI.create(request.uri());
                            String query = uri.getRawQuery();
                            captures.add(new RequestCapture(
                                    request.method().name(),
                                    uri.getPath(),
                                    query,
                                    body,
                                    request.requestHeaders().get("Authorization"),
                                    request.requestHeaders().get(TRACEPARENT_HEADER)
                            ));
                            if (query != null && query.contains("upstreamCors=true")) {
                                response.header("Access-Control-Allow-Origin", "http://localhost:12881");
                                response.header("Access-Control-Allow-Credentials", "true");
                            }
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
