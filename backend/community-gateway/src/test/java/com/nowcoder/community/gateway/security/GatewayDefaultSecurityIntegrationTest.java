package com.nowcoder.community.gateway.security;

import com.nowcoder.community.gateway.CommunityGatewayApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityGatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class GatewayDefaultSecurityIntegrationTest {

    private static final String METRICS_USERNAME = "prometheus";
    private static final String METRICS_PASSWORD = "gateway-metrics-pass-please-change";
    private static volatile DisposableServer httpUpstream;
    private static volatile DisposableServer workerServer;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.http.routes[0].id", () -> "bootstrap-api");
        registry.add("gateway.http.routes[0].path-prefix", () -> "/api");
        registry.add("gateway.http.routes[0].uri", GatewayDefaultSecurityIntegrationTest::httpUpstreamBaseUrl);
        registry.add("gateway.ws.proxy.path", () -> "/ws/im");
        registry.add("gateway.ws.proxy.auth-required", () -> false);
        registry.add("gateway.ws.proxy.default-worker-uri", GatewayDefaultSecurityIntegrationTest::workerUri);
        registry.add("security.jwt.hmac-secret", () -> "gateway-test-jwt-secret-please-change-123456");
        registry.add("community.metrics.basic-auth.username", () -> METRICS_USERNAME);
        registry.add("community.metrics.basic-auth.password", () -> METRICS_PASSWORD);
    }

    @AfterAll
    static void tearDown() {
        if (httpUpstream != null) {
            httpUpstream.disposeNow();
            httpUpstream = null;
        }
        if (workerServer != null) {
            workerServer.disposeNow();
            workerServer = null;
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
    void shouldAllowWebSocketHandshakeOnDefaultGatewaySecurityChain() throws Exception {
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();

        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
        URI uri = URI.create("ws://127.0.0.1:" + port + "/ws/im");

        Disposable handle = client.execute(uri, session -> {
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
            outbound.tryEmitNext("hello");
            outbound.tryEmitComplete();
            assertThat(received.poll(5, TimeUnit.SECONDS)).isEqualTo("worker:hello");
        } finally {
            handle.dispose();
        }
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

    private static synchronized String workerUri() {
        if (workerServer == null) {
            workerServer = HttpServer.create()
                    .host("127.0.0.1")
                    .port(0)
                    .route(routes -> routes.ws("/internal/ws/im", (in, out) ->
                            out.sendString(in.receive().asString().map(text -> "worker:" + text))))
                    .bindNow(Duration.ofSeconds(5));
        }
        return "ws://127.0.0.1:" + workerServer.port() + "/internal/ws/im";
    }
}
