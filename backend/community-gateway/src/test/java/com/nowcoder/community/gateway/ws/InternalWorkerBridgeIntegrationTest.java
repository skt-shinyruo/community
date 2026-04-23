package com.nowcoder.community.gateway.ws;

import com.nowcoder.community.gateway.CommunityGatewayApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {
                CommunityGatewayApplication.class,
                InternalWorkerBridgeIntegrationTest.PermitAllSecurityConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class InternalWorkerBridgeIntegrationTest {

    private static volatile DisposableServer workerA;
    private static volatile DisposableServer workerB;
    private static final LinkedBlockingQueue<String> WORKER_A_INBOUND = new LinkedBlockingQueue<>();
    private static final LinkedBlockingQueue<String> WORKER_B_INBOUND = new LinkedBlockingQueue<>();
    private static final LinkedBlockingQueue<Map<String, String>> WORKER_A_HANDSHAKES = new LinkedBlockingQueue<>();
    private static final LinkedBlockingQueue<Map<String, String>> WORKER_B_HANDSHAKES = new LinkedBlockingQueue<>();

    @Autowired
    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.ws.proxy.path", () -> "/ws/im/workers/**");
        registry.add("gateway.ws.discovery.service-id", () -> "im-realtime-worker");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].uri", InternalWorkerBridgeIntegrationTest::workerAHttpUri);
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].metadata.workerId", () -> "worker-a");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].metadata.wsPath", () -> "/internal/ws/im");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].metadata.wsPort", () -> String.valueOf(workerAPort()));
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[1].uri", InternalWorkerBridgeIntegrationTest::workerBHttpUri);
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[1].metadata.workerId", () -> "worker-b");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[1].metadata.wsPath", () -> "/internal/ws/im");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[1].metadata.wsPort", () -> String.valueOf(workerBPort()));
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("security.jwt.hmac-secret", () -> "gateway-test-jwt-secret-please-change-123456");
    }

    @AfterAll
    static void tearDown() {
        if (workerA != null) {
            workerA.disposeNow();
            workerA = null;
        }
        if (workerB != null) {
            workerB.disposeNow();
            workerB = null;
        }
    }

    @Test
    void shouldSelectWorkerByPathAndForwardFrames() throws Exception {
        WORKER_A_INBOUND.clear();
        WORKER_B_INBOUND.clear();

        List<String> received = runSession("/ws/im/workers/worker-b", List.of("hello", "ping"), 2);

        assertThat(received).containsExactly("worker-b:hello", "worker-b:ping");
        assertThat(WORKER_A_INBOUND.poll(200, TimeUnit.MILLISECONDS)).isNull();
        assertThat(WORKER_B_INBOUND.poll(5, TimeUnit.SECONDS)).isEqualTo("hello");
        assertThat(WORKER_B_INBOUND.poll(5, TimeUnit.SECONDS)).isEqualTo("ping");
    }

    @Test
    void shouldForwardTraceHeadersFromExternalHandshakeToWorkerHandshake() throws Exception {
        WORKER_A_HANDSHAKES.clear();
        WORKER_B_HANDSHAKES.clear();

        String traceId = "11111111111111111111111111111111";
        String traceparent = "00-" + traceId + "-2222222222222222-01";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Id", traceId);
        headers.set("traceparent", traceparent);

        List<String> received = runSession("/ws/im/workers/worker-b", List.of("hello"), 1, headers);

        assertThat(received).containsExactly("worker-b:hello");
        assertThat(WORKER_A_HANDSHAKES.poll(200, TimeUnit.MILLISECONDS)).isNull();
        assertThat(WORKER_B_HANDSHAKES.poll(5, TimeUnit.SECONDS))
                .containsEntry("X-Trace-Id", traceId)
                .containsEntry("traceparent", traceparent);
    }

    @Test
    void shouldForwardGatewayGeneratedTraceHeadersWhenBrowserHandshakeHasNoCustomHeaders() throws Exception {
        WORKER_B_HANDSHAKES.clear();

        List<String> received = runSession("/ws/im/workers/worker-b", List.of("hello"), 1);

        assertThat(received).containsExactly("worker-b:hello");
        Map<String, String> handshake = WORKER_B_HANDSHAKES.poll(5, TimeUnit.SECONDS);
        assertThat(handshake).isNotNull();
        String traceId = handshake.get("X-Trace-Id");
        String traceparent = handshake.get("traceparent");
        assertThat(traceId).matches("^[0-9a-f]{32}$");
        assertThat(traceparent)
                .startsWith("00-" + traceId + "-")
                .endsWith("-01");
    }

    private List<String> runSession(String path, List<String> outboundFrames, int expectedMessages) throws Exception {
        return runSession(path, outboundFrames, expectedMessages, new HttpHeaders());
    }

    private List<String> runSession(String path, List<String> outboundFrames, int expectedMessages, HttpHeaders headers) throws Exception {
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();

        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
        URI gatewayUri = URI.create("ws://localhost:" + port + path);

        Disposable sessionHandle = client.execute(gatewayUri, headers, session -> {
                    Mono<Void> send = session.send(outbound.asFlux().map(session::textMessage));
                    Mono<Void> receive = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .doOnNext(received::offer)
                            .take(expectedMessages)
                            .then();
                    return Mono.when(send, receive);
                })
                .subscribe();
        try {
            for (String frame : outboundFrames) {
                outbound.tryEmitNext(frame);
            }
            outbound.tryEmitComplete();

            ArrayList<String> messages = new ArrayList<>(expectedMessages);
            for (int i = 0; i < expectedMessages; i++) {
                messages.add(received.poll(5, TimeUnit.SECONDS));
            }
            return messages;
        } finally {
            sessionHandle.dispose();
        }
    }

    private static synchronized String workerAHttpUri() {
        if (workerA == null) {
            workerA = startWorker("worker-a", WORKER_A_INBOUND, WORKER_A_HANDSHAKES);
        }
        return "http://127.0.0.1:" + workerA.port();
    }

    private static synchronized int workerAPort() {
        if (workerA == null) {
            workerA = startWorker("worker-a", WORKER_A_INBOUND, WORKER_A_HANDSHAKES);
        }
        return workerA.port();
    }

    private static synchronized String workerBHttpUri() {
        if (workerB == null) {
            workerB = startWorker("worker-b", WORKER_B_INBOUND, WORKER_B_HANDSHAKES);
        }
        return "http://127.0.0.1:" + workerB.port();
    }

    private static synchronized int workerBPort() {
        if (workerB == null) {
            workerB = startWorker("worker-b", WORKER_B_INBOUND, WORKER_B_HANDSHAKES);
        }
        return workerB.port();
    }

    private static DisposableServer startWorker(
            String workerId,
            LinkedBlockingQueue<String> inboundFrames,
            LinkedBlockingQueue<Map<String, String>> handshakeHeaders
    ) {
        return HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.ws("/internal/ws/im", (in, out) ->
                        out.sendString(in.receive()
                                .asString()
                                .doOnSubscribe(ignored -> handshakeHeaders.offer(Map.of(
                                        "X-Trace-Id", normalizeHeader(in.headers().get("X-Trace-Id")),
                                        "traceparent", normalizeHeader(in.headers().get("traceparent"))
                                )))
                                .doOnNext(inboundFrames::offer)
                                .map(text -> workerId + ":" + text))
                ))
                .bindNow(Duration.ofSeconds(5));
    }

    private static String normalizeHeader(String value) {
        return value == null ? "" : value;
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
