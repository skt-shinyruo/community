package com.nowcoder.community.gateway.im;

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
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {
                CommunityGatewayApplication.class,
                GatewayImEdgeRouteIntegrationTest.PermitAllSecurityConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class GatewayImEdgeRouteIntegrationTest {

    private static final LinkedBlockingQueue<RequestCapture> IM_EDGE_CAPTURES = new LinkedBlockingQueue<>();
    private static final LinkedBlockingQueue<RequestCapture> IM_CORE_CAPTURES = new LinkedBlockingQueue<>();
    private static final String TRACEPARENT_HEADER = "traceparent";

    private static volatile DisposableServer imEdgeServer;
    private static volatile DisposableServer imCoreServer;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.http.routes[0].id", () -> "bootstrap-api");
        registry.add("gateway.http.routes[0].path-prefix", () -> "/api");
        registry.add("gateway.http.routes[0].service-id", () -> "community-app");
        registry.add("gateway.http.routes[1].id", () -> "im-core");
        registry.add("gateway.http.routes[1].path-prefix", () -> "/api/im");
        registry.add("gateway.http.routes[1].service-id", () -> "im-core");
        registry.add("gateway.im-edge.service-id", () -> "community-im-gateway");
        registry.add("gateway.im-edge.session-path", () -> "/api/im/sessions");
        registry.add("gateway.im-edge.ws-path", () -> "/ws/im");
        registry.add("spring.cloud.discovery.client.simple.instances.community-im-gateway[0].uri",
                () -> "http://127.0.0.1:" + imEdgePort());
        registry.add("spring.cloud.discovery.client.simple.instances.im-core[0].uri",
                () -> "http://127.0.0.1:" + imCorePort());
        registry.add("spring.cloud.discovery.client.simple.instances.community-app[0].uri",
                () -> "http://127.0.0.1:" + imCorePort());
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("security.jwt.hmac-secret", () -> "gateway-test-jwt-secret-please-change-123456");
    }

    @AfterAll
    static void stopServers() {
        if (imEdgeServer != null) {
            imEdgeServer.disposeNow();
            imEdgeServer = null;
        }
        if (imCoreServer != null) {
            imCoreServer.disposeNow();
            imCoreServer = null;
        }
    }

    @Test
    void shouldRouteSessionBootstrapToImEdgeBeforeGenericImCoreRoute() throws Exception {
        IM_EDGE_CAPTURES.clear();
        IM_CORE_CAPTURES.clear();

        webTestClient.post()
                .uri("/api/im/sessions")
                .header("Authorization", "Bearer edge-token")
                .header(TRACEPARENT_HEADER, traceparent("cccccccccccccccccccccccccccccccc"))
                .bodyValue("{\"conversationId\":\"c1\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.upstream").isEqualTo("im-edge");

        RequestCapture capture = IM_EDGE_CAPTURES.poll(5, TimeUnit.SECONDS);
        assertThat(capture).isNotNull();
        assertThat(capture.method()).isEqualTo("POST");
        assertThat(capture.path()).isEqualTo("/api/im/sessions");
        assertThat(capture.body()).isEqualTo("{\"conversationId\":\"c1\"}");
        assertThat(capture.authorization()).isEqualTo("Bearer edge-token");
        assertThat(capture.traceparent()).isEqualTo(traceparent("cccccccccccccccccccccccccccccccc"));
        assertThat(IM_CORE_CAPTURES).isEmpty();
    }

    @Test
    void shouldKeepGenericImApiTrafficOnImCore() throws Exception {
        IM_EDGE_CAPTURES.clear();
        IM_CORE_CAPTURES.clear();

        webTestClient.get()
                .uri("/api/im/conversations?unreadOnly=true")
                .header("Authorization", "Bearer core-token")
                .header(TRACEPARENT_HEADER, traceparent("dddddddddddddddddddddddddddddddd"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.upstream").isEqualTo("im-core");

        RequestCapture capture = IM_CORE_CAPTURES.poll(5, TimeUnit.SECONDS);
        assertThat(capture).isNotNull();
        assertThat(capture.method()).isEqualTo("GET");
        assertThat(capture.path()).isEqualTo("/api/im/conversations");
        assertThat(capture.query()).isEqualTo("unreadOnly=true");
        assertThat(capture.authorization()).isEqualTo("Bearer core-token");
        assertThat(capture.traceparent()).isEqualTo(traceparent("dddddddddddddddddddddddddddddddd"));
        assertThat(IM_EDGE_CAPTURES).isEmpty();
    }

    @Test
    void shouldRoutePublicImWebSocketToImEdge() throws Exception {
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        CountDownLatch connected = new CountDownLatch(1);

        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
        URI gatewayUri = URI.create("ws://localhost:" + port + "/ws/im");

        Disposable ws = client.execute(gatewayUri, session -> {
                    connected.countDown();
                    Mono<Void> send = session.send(outbound.asFlux().map(session::textMessage));
                    Mono<Void> recv = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .doOnNext(received::offer)
                            .take(1)
                            .then();
                    return Mono.when(send, recv);
                })
                .subscribe();
        try {
            assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();
            outbound.tryEmitNext("hello");
            outbound.tryEmitComplete();
            assertThat(received.poll(5, TimeUnit.SECONDS)).isEqualTo("im-edge:hello");
        } finally {
            ws.dispose();
        }
    }

    private static synchronized int imEdgePort() {
        if (imEdgeServer == null) {
            imEdgeServer = HttpServer.create()
                    .host("127.0.0.1")
                    .port(0)
                    .route(routes -> routes
                            .post("/api/im/sessions", (request, response) -> request.receive()
                                    .aggregate()
                                    .asString()
                                    .defaultIfEmpty("")
                                    .flatMap(body -> {
                                        URI uri = URI.create(request.uri());
                                        IM_EDGE_CAPTURES.add(new RequestCapture(
                                                request.method().name(),
                                                uri.getPath(),
                                                uri.getRawQuery(),
                                                body,
                                                request.requestHeaders().get("Authorization"),
                                                request.requestHeaders().get(TRACEPARENT_HEADER)
                                        ));
                                        return response.header("Content-Type", "application/json")
                                                .sendString(Mono.just("{\"upstream\":\"im-edge\"}"))
                                                .then();
                                    }))
                            .ws("/ws/im", (in, out) ->
                                    out.sendString(in.receive().asString().map(text -> "im-edge:" + text))
                            ))
                    .bindNow(Duration.ofSeconds(5));
        }
        return imEdgeServer.port();
    }

    private static synchronized int imCorePort() {
        if (imCoreServer == null) {
            imCoreServer = HttpServer.create()
                    .host("127.0.0.1")
                    .port(0)
                    .handle((request, response) -> request.receive()
                            .aggregate()
                            .asString()
                            .defaultIfEmpty("")
                            .flatMap(body -> {
                                URI uri = URI.create(request.uri());
                                IM_CORE_CAPTURES.add(new RequestCapture(
                                        request.method().name(),
                                        uri.getPath(),
                                        uri.getRawQuery(),
                                        body,
                                        request.requestHeaders().get("Authorization"),
                                        request.requestHeaders().get(TRACEPARENT_HEADER)
                                ));
                                return response.header("Content-Type", "application/json")
                                        .sendString(Mono.just("{\"upstream\":\"im-core\"}"))
                                        .then();
                            }))
                    .bindNow(Duration.ofSeconds(5));
        }
        return imCoreServer.port();
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
