package com.nowcoder.community.im.gateway.ws;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.im.gateway.CommunityImGatewayApplication;
import com.nowcoder.community.im.gateway.session.SessionTicketCodec;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityImGatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ImEdgeWebSocketBridgeIntegrationTest {

    private static final String SECRET = "im-gateway-ws-test-secret-please-change-123456";
    private static volatile DisposableServer workerServer;
    private static volatile DisposableServer binaryWorkerServer;
    private static final LinkedBlockingQueue<Map<String, String>> WORKER_HANDSHAKES = new LinkedBlockingQueue<>();

    @LocalServerPort
    int port;

    @Autowired
    ReactorNettyWebSocketClient client;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.hmac-secret", () -> SECRET);
        registry.add("security.jwt.issuer", () -> "community-auth");
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("im.gateway.ws.first-frame-timeout-ms", () -> "100");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].uri",
                () -> "http://127.0.0.1:" + workerPort());
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].metadata.workerId",
                () -> "worker-a");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].metadata.wsPath",
                () -> "/internal/ws/im");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].metadata.wsPort",
                () -> String.valueOf(workerPort()));
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[1].uri",
                () -> "http://127.0.0.1:" + binaryWorkerPort());
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[1].metadata.workerId",
                () -> "worker-binary");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[1].metadata.wsPath",
                () -> "/internal/ws/im");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[1].metadata.wsPort",
                () -> String.valueOf(binaryWorkerPort()));
    }

    @AfterAll
    static void stopWorker() {
        if (workerServer != null) {
            workerServer.disposeNow();
            workerServer = null;
        }
        if (binaryWorkerServer != null) {
            binaryWorkerServer.disposeNow();
            binaryWorkerServer = null;
        }
    }

    @Test
    void shouldBridgeStableWsPathToWorkerFromConnectTicket() throws Exception {
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();

        Disposable handle = client.execute(externalUri(), session -> {
                    Mono<Void> send = session.send(outbound.asFlux().map(session::textMessage));
                    Mono<Void> receive = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .doOnNext(received::offer)
                            .take(2)
                            .then();
                    return Mono.when(send, receive);
                })
                .subscribe();
        try {
            String ticket = ticket("worker-a");
            String connect = "{\"type\":\"connect\",\"ticket\":\"" + ticket + "\"}";
            String ping = "{\"type\":\"ping\",\"ts\":1}";
            outbound.tryEmitNext(connect);
            outbound.tryEmitNext(ping);
            outbound.tryEmitComplete();

            assertThat(received.poll(5, TimeUnit.SECONDS)).isEqualTo("worker-a:" + connect);
            assertThat(received.poll(5, TimeUnit.SECONDS)).isEqualTo("worker-a:" + ping);
        } finally {
            handle.dispose();
        }
    }

    @Test
    void shouldRejectNonConnectFirstFrame() throws Exception {
        String reject = exchangeTextUntilReject("{\"type\":\"ping\"}");

        assertThat(reject).contains("\"reasonCode\":\"connect_required\"");
    }

    @Test
    void shouldRejectInvalidTicket() throws Exception {
        String reject = exchangeTextUntilReject("{\"type\":\"connect\",\"ticket\":\"not-a-ticket\"}");

        assertThat(reject).contains("\"reasonCode\":\"invalid_ticket\"");
    }

    @Test
    void shouldRejectUnavailableWorkerId() throws Exception {
        String reject = exchangeTextUntilReject(
                "{\"type\":\"connect\",\"ticket\":\"" + ticket("worker-missing") + "\"}"
        );

        assertThat(reject).contains("\"reasonCode\":\"worker_unavailable\"");
    }

    @Test
    void shouldRejectWhenFirstFrameTimesOut() throws Exception {
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();

        Disposable handle = client.execute(externalUri(), session -> session.receive()
                        .map(WebSocketMessage::getPayloadAsText)
                        .doOnNext(received::offer)
                        .take(1)
                        .then())
                .subscribe();
        try {
            String reject = received.poll(5, TimeUnit.SECONDS);
            assertThat(reject).isNotNull();
            assertThat(reject).contains("\"reasonCode\":\"connect_timeout\"");
        } finally {
            handle.dispose();
        }
    }

    @Test
    void shouldRejectBinaryFirstFrame() throws Exception {
        String reject = exchangeBinaryFirstFrameUntilReject();

        assertThat(reject).contains("\"reasonCode\":\"unsupported_frame_type\"");
    }

    @Test
    void shouldRejectMalformedJsonFirstFrame() throws Exception {
        String reject = exchangeTextUntilReject("{not-json");

        assertThat(reject).contains("\"reasonCode\":\"malformed_frame\"");
    }

    @Test
    void shouldRejectBlankFirstFrame() throws Exception {
        String reject = exchangeTextUntilReject("   ");

        assertThat(reject).contains("\"reasonCode\":\"malformed_frame\"");
    }

    @Test
    void shouldCloseWhenSubsequentFrameIsBinary() throws Exception {
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<WebSocketMessage> outbound = Sinks.many().unicast().onBackpressureBuffer();
        String connect = "{\"type\":\"connect\",\"ticket\":\"" + ticket("worker-a") + "\"}";

        Disposable handle = client.execute(externalUri(), session -> {
                    Mono<Void> send = session.send(outbound.asFlux()
                            .startWith(session.textMessage(connect)));
                    Mono<Void> receive = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .doOnNext(received::offer)
                            .then();
                    outbound.tryEmitNext(session.binaryMessage(factory -> factory.wrap(new byte[]{1, 2, 3})));
                    outbound.tryEmitComplete();
                    return Mono.when(send, receive);
                })
                .subscribe();
        try {
            String first = received.poll(1, TimeUnit.SECONDS);
            if (first != null) {
                assertThat(first).isEqualTo("worker-a:" + connect);
            }
            assertThat(received.poll(1, TimeUnit.SECONDS)).isNull();
        } finally {
            handle.dispose();
        }
    }

    @Test
    void shouldCloseWhenWorkerEmitsBinaryFrameWithoutTextConversion() throws Exception {
        LinkedBlockingQueue<WebSocketMessage.Type> receivedTypes = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<String> receivedText = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();

        Disposable handle = client.execute(externalUri(), session -> {
                    Mono<Void> send = session.send(outbound.asFlux().map(session::textMessage));
                    Mono<Void> receive = session.receive()
                            .doOnNext(message -> {
                                receivedTypes.offer(message.getType());
                                if (message.getType() == WebSocketMessage.Type.TEXT) {
                                    receivedText.offer(message.getPayloadAsText());
                                }
                            })
                            .then();
                    return Mono.when(send, receive);
                })
                .subscribe();
        try {
            String connect = "{\"type\":\"connect\",\"ticket\":\"" + ticket("worker-binary") + "\"}";
            outbound.tryEmitNext(connect);
            outbound.tryEmitComplete();

            assertThat(receivedTypes.poll(1, TimeUnit.SECONDS)).isNull();
            assertThat(receivedText.poll(1, TimeUnit.SECONDS)).isNull();
        } finally {
            handle.dispose();
        }
    }

    @Test
    void shouldForwardTraceHeadersToInternalWorkerHandshake() throws Exception {
        WORKER_HANDSHAKES.clear();
        String traceId = "11111111111111111111111111111111";
        String traceparent = "00-" + traceId + "-2222222222222222-01";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Id", traceId);
        headers.set("traceparent", traceparent);

        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();

        Disposable handle = client.execute(externalUri(), headers, session -> {
                    Mono<Void> send = session.send(outbound.asFlux().map(session::textMessage));
                    Mono<Void> receive = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .doOnNext(received::offer)
                            .take(1)
                            .then();
                    return Mono.when(send, receive);
                })
                .subscribe();
        try {
            String connect = "{\"type\":\"connect\",\"ticket\":\"" + ticket("worker-a") + "\"}";
            outbound.tryEmitNext(connect);
            outbound.tryEmitComplete();

            assertThat(received.poll(5, TimeUnit.SECONDS)).isEqualTo("worker-a:" + connect);
            assertThat(WORKER_HANDSHAKES.poll(5, TimeUnit.SECONDS))
                    .containsEntry("X-Trace-Id", traceId)
                    .containsEntry("traceparent", traceparent);
        } finally {
            handle.dispose();
        }
    }

    private String exchangeTextUntilReject(String firstFrame) throws InterruptedException {
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();

        Disposable handle = client.execute(externalUri(), session -> {
                    Mono<Void> send = session.send(outbound.asFlux().map(session::textMessage));
                    Mono<Void> receive = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .doOnNext(received::offer)
                            .take(1)
                            .then();
                    return Mono.when(send, receive);
                })
                .subscribe();
        try {
            outbound.tryEmitNext(firstFrame);
            outbound.tryEmitComplete();
            String reject = received.poll(5, TimeUnit.SECONDS);
            assertThat(reject).isNotNull();
            return reject;
        } finally {
            handle.dispose();
        }
    }

    private String exchangeBinaryFirstFrameUntilReject() throws InterruptedException {
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();

        Disposable handle = client.execute(externalUri(), session -> {
                    Mono<Void> send = session.send(Flux.just(
                            session.binaryMessage(factory -> factory.wrap(new byte[]{1, 2, 3}))
                    ));
                    Mono<Void> receive = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .doOnNext(received::offer)
                            .take(1)
                            .then();
                    return Mono.when(send, receive);
                })
                .subscribe();
        try {
            String reject = received.poll(5, TimeUnit.SECONDS);
            assertThat(reject).isNotNull();
            return reject;
        } finally {
            handle.dispose();
        }
    }

    private URI externalUri() {
        return URI.create("ws://127.0.0.1:" + port + "/ws/im");
    }

    private static synchronized int workerPort() {
        if (workerServer == null) {
            workerServer = HttpServer.create()
                    .host("127.0.0.1")
                    .port(0)
                    .route(routes -> routes.ws("/internal/ws/im", (in, out) ->
                            out.sendString(in.receive()
                                    .asString()
                                    .doOnSubscribe(ignored -> WORKER_HANDSHAKES.offer(Map.of(
                                            "X-Trace-Id", normalizeHeader(in.headers().get("X-Trace-Id")),
                                            "traceparent", normalizeHeader(in.headers().get("traceparent"))
                                    )))
                                    .map(text -> "worker-a:" + text))
                    ))
                    .bindNow(Duration.ofSeconds(5));
        }
        return workerServer.port();
    }

    private static synchronized int binaryWorkerPort() {
        if (binaryWorkerServer == null) {
            binaryWorkerServer = HttpServer.create()
                    .host("127.0.0.1")
                    .port(0)
                    .route(routes -> routes.ws("/internal/ws/im", (in, out) ->
                            out.sendObject(in.receive()
                                    .asString()
                                    .take(1)
                                    .map(ignored -> new BinaryWebSocketFrame(Unpooled.wrappedBuffer(new byte[]{1, 2, 3}))))
                    ))
                    .bindNow(Duration.ofSeconds(5));
        }
        return binaryWorkerServer.port();
    }

    private static String normalizeHeader(String value) {
        return value == null ? "" : value;
    }

    private static String ticket(String workerId) {
        JwtProperties properties = jwtProperties();
        SessionTicketCodec codec = new SessionTicketCodec(properties, JwtCodecs.jwtDecoder(properties));
        return codec.encode(
                "sess-1",
                UUID.fromString("00000000-0000-7000-8000-000000000123"),
                workerId,
                Instant.now().plusSeconds(120)
        );
    }

    private static JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setHmacSecret(SECRET);
        properties.setIssuer("community-auth");
        return properties;
    }
}
