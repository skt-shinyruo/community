package com.nowcoder.community.gateway.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nowcoder.community.gateway.CommunityGatewayApplication;
import com.nowcoder.community.gateway.shard.ConsistentHashShardRouter;
import com.nowcoder.community.gateway.shard.ShardRouter;
import com.nowcoder.community.gateway.shard.WorkerDescriptor;
import com.nowcoder.community.gateway.shard.WorkerRegistry;
import com.nowcoder.community.gateway.shard.WorkerRegistryProperties;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
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

    private static final String JWT_SECRET = "gateway-test-jwt-secret-please-change-123456";
    private static final String JWT_ISSUER = "community-auth";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        registry.add("gateway.ws.proxy.path", () -> "/ws/im");
        registry.add("gateway.ws.proxy.auth-required", () -> true);
        registry.add("gateway.ws.proxy.default-worker-uri", InternalWorkerBridgeIntegrationTest::workerAUri);
        registry.add("gateway.ws.shard.workers[0].id", () -> "worker-a");
        registry.add("gateway.ws.shard.workers[0].uri", InternalWorkerBridgeIntegrationTest::workerAUri);
        registry.add("gateway.ws.shard.workers[1].id", () -> "worker-b");
        registry.add("gateway.ws.shard.workers[1].uri", InternalWorkerBridgeIntegrationTest::workerBUri);
        registry.add("security.jwt.hmac-secret", () -> JWT_SECRET);
        registry.add("security.jwt.issuer", () -> JWT_ISSUER);
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
    void shouldSelectShardWorkerByUserIdAndForwardAuthBeforeSubsequentFrames() throws Exception {
        WORKER_A_INBOUND.clear();
        WORKER_B_INBOUND.clear();
        WORKER_A_HANDSHAKES.clear();
        WORKER_B_HANDSHAKES.clear();

        String userId = userIdFor("worker-b");
        String token = signHs256(JWT_SECRET, JWT_ISSUER, userId, Instant.now().plusSeconds(120));
        List<String> received = runSession(List.of(
                "{\"type\":\"auth\",\"accessToken\":\"" + token + "\"}",
                "{\"type\":\"ping\"}"
        ), 2);

        JsonNode authOk = OBJECT_MAPPER.readTree(received.get(0));
        JsonNode pong = OBJECT_MAPPER.readTree(received.get(1));

        assertThat(authOk.path("type").asText("")).isEqualTo("auth_ok");
        assertThat(authOk.path("workerId").asText("")).isEqualTo("worker-b");
        assertThat(authOk.path("userId").asText("")).isEqualTo(userId);
        assertThat(pong.path("type").asText("")).isEqualTo("pong");
        assertThat(pong.path("workerId").asText("")).isEqualTo("worker-b");

        assertThat(WORKER_A_INBOUND.poll(200, TimeUnit.MILLISECONDS)).isNull();
        assertThat(WORKER_B_INBOUND.poll(5, TimeUnit.SECONDS)).contains("\"type\":\"auth\"");
        assertThat(WORKER_B_INBOUND.poll(5, TimeUnit.SECONDS)).contains("\"type\":\"ping\"");
    }

    @Test
    void shouldForwardTraceHeadersFromExternalHandshakeToWorkerHandshake() throws Exception {
        WORKER_A_INBOUND.clear();
        WORKER_B_INBOUND.clear();
        WORKER_A_HANDSHAKES.clear();
        WORKER_B_HANDSHAKES.clear();

        String userId = userIdFor("worker-b");
        String token = signHs256(JWT_SECRET, JWT_ISSUER, userId, Instant.now().plusSeconds(120));
        String traceId = "11111111111111111111111111111111";
        String traceparent = "00-" + traceId + "-2222222222222222-01";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Id", traceId);
        headers.set("traceparent", traceparent);

        List<String> received = runSession(List.of(
                "{\"type\":\"auth\",\"accessToken\":\"" + token + "\"}",
                "{\"type\":\"ping\"}"
        ), 2, headers);

        JsonNode authOk = OBJECT_MAPPER.readTree(received.get(0));
        JsonNode pong = OBJECT_MAPPER.readTree(received.get(1));

        assertThat(authOk.path("type").asText("")).isEqualTo("auth_ok");
        assertThat(pong.path("type").asText("")).isEqualTo("pong");
        assertThat(WORKER_A_HANDSHAKES.poll(200, TimeUnit.MILLISECONDS)).isNull();
        assertThat(WORKER_B_HANDSHAKES.poll(5, TimeUnit.SECONDS))
                .containsEntry("X-Trace-Id", traceId)
                .containsEntry("traceparent", traceparent);
    }

    @Test
    void shouldForwardGatewayGeneratedTraceHeadersWhenBrowserHandshakeHasNoCustomHeaders() throws Exception {
        WORKER_A_INBOUND.clear();
        WORKER_B_INBOUND.clear();
        WORKER_A_HANDSHAKES.clear();
        WORKER_B_HANDSHAKES.clear();

        String userId = userIdFor("worker-b");
        String token = signHs256(JWT_SECRET, JWT_ISSUER, userId, Instant.now().plusSeconds(120));

        List<String> received = runSession(List.of(
                "{\"type\":\"auth\",\"accessToken\":\"" + token + "\"}",
                "{\"type\":\"ping\"}"
        ), 2);

        JsonNode authOk = OBJECT_MAPPER.readTree(received.get(0));
        JsonNode pong = OBJECT_MAPPER.readTree(received.get(1));

        assertThat(authOk.path("type").asText("")).isEqualTo("auth_ok");
        assertThat(pong.path("type").asText("")).isEqualTo("pong");

        Map<String, String> handshake = WORKER_B_HANDSHAKES.poll(5, TimeUnit.SECONDS);
        assertThat(handshake).isNotNull();
        String traceId = handshake.get("X-Trace-Id");
        String traceparent = handshake.get("traceparent");
        assertThat(traceId).matches("^[0-9a-f]{32}$");
        assertThat(traceparent)
                .startsWith("00-" + traceId + "-")
                .endsWith("-01");
    }

    private List<String> runSession(List<String> outboundFrames, int expectedMessages) throws Exception {
        return runSession(outboundFrames, expectedMessages, new HttpHeaders());
    }

    private List<String> runSession(List<String> outboundFrames, int expectedMessages, HttpHeaders headers) throws Exception {
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();

        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
        URI gatewayUri = URI.create("ws://localhost:" + port + "/ws/im");

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

    private static synchronized String workerAUri() {
        if (workerA == null) {
            workerA = startWorker("worker-a", WORKER_A_INBOUND, WORKER_A_HANDSHAKES);
        }
        return "ws://127.0.0.1:" + workerA.port() + "/internal/ws/im";
    }

    private static synchronized String workerBUri() {
        if (workerB == null) {
            workerB = startWorker("worker-b", WORKER_B_INBOUND, WORKER_B_HANDSHAKES);
        }
        return "ws://127.0.0.1:" + workerB.port() + "/internal/ws/im";
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
                                .handle((text, sink) -> {
                                    JsonNode node = parse(text);
                                    String type = node.path("type").asText("");
                                    if ("auth".equals(type)) {
                                        sink.next("{\"type\":\"auth_ok\",\"userId\":\""
                                                + jwtSubject(node.path("accessToken").asText(""))
                                                + "\",\"workerId\":\"" + workerId + "\"}");
                                        return;
                                    }
                                    if ("ping".equals(type)) {
                                        sink.next("{\"type\":\"pong\",\"workerId\":\"" + workerId + "\"}");
                                        return;
                                    }
                                    sink.next("{\"type\":\"echo\",\"workerId\":\"" + workerId + "\",\"payload\":" + quote(text) + "}");
                                }))
                ))
                .bindNow(Duration.ofSeconds(5));
    }

    private static String normalizeHeader(String value) {
        return value == null ? "" : value;
    }

    private static String jwtSubject(String token) {
        try {
            return SignedJWT.parse(token).getJWTClaimsSet().getSubject();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static JsonNode parse(String text) {
        try {
            return OBJECT_MAPPER.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static String quote(String text) {
        try {
            return OBJECT_MAPPER.writeValueAsString(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String userIdFor(String workerId) {
        WorkerRegistryProperties properties = new WorkerRegistryProperties();
        properties.getWorkers().add(worker("worker-a", workerAUri()));
        properties.getWorkers().add(worker("worker-b", workerBUri()));
        ShardRouter router = new ConsistentHashShardRouter(new WorkerRegistry(properties));
        for (int candidate = 1; candidate <= 10_000; candidate++) {
            String userId = String.valueOf(candidate);
            if (router.route(userId).map(WorkerDescriptor::getId).filter(workerId::equals).isPresent()) {
                return userId;
            }
        }
        throw new IllegalStateException("No userId routed to " + workerId);
    }

    private static WorkerRegistryProperties.Worker worker(String id, String uri) {
        WorkerRegistryProperties.Worker worker = new WorkerRegistryProperties.Worker();
        worker.setId(id);
        worker.setUri(URI.create(uri));
        return worker;
    }

    private static String signHs256(String secret, String issuer, String subject, Instant expiresAt) throws Exception {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .expirationTime(Date.from(expiresAt))
                .issueTime(new Date());
        if (issuer != null && !issuer.isBlank()) {
            claims.issuer(issuer);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        jwt.sign(new MACSigner(secretBytes));
        return jwt.serialize();
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
