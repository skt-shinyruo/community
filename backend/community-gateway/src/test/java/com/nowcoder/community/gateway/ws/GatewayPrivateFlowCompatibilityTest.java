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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {
                CommunityGatewayApplication.class,
                GatewayPrivateFlowCompatibilityTest.PermitAllSecurityConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class GatewayPrivateFlowCompatibilityTest {

    private static final String JWT_SECRET = "gateway-test-jwt-secret-please-change-123456";
    private static final String JWT_ISSUER = "community-auth";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static volatile DisposableServer workerA;
    private static volatile DisposableServer workerB;

    @Autowired
    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.ws.proxy.path", () -> "/ws/im");
        registry.add("gateway.ws.proxy.auth-required", () -> true);
        registry.add("gateway.ws.proxy.default-worker-uri", GatewayPrivateFlowCompatibilityTest::workerAUri);
        registry.add("gateway.ws.discovery.service-id", () -> "im-realtime-worker");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].uri", GatewayPrivateFlowCompatibilityTest::workerAHttpUri);
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].metadata.workerId", () -> "worker-a");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].metadata.wsPath", () -> "/internal/ws/im");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[0].metadata.wsPort", () -> String.valueOf(workerAPort()));
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[1].uri", GatewayPrivateFlowCompatibilityTest::workerBHttpUri);
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[1].metadata.workerId", () -> "worker-b");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[1].metadata.wsPath", () -> "/internal/ws/im");
        registry.add("spring.cloud.discovery.client.simple.instances.im-realtime-worker[1].metadata.wsPort", () -> String.valueOf(workerBPort()));
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
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
    void shouldPreservePrivateMessageHandshakeAndAckThroughChosenWorker() throws Exception {
        String userId = userIdFor("worker-b");
        String token = signHs256(JWT_SECRET, JWT_ISSUER, userId, Instant.now().plusSeconds(120));

        List<String> received = runSession(List.of(
                "{\"type\":\"auth\",\"accessToken\":\"" + token + "\"}",
                "{\"type\":\"sendPrivateText\",\"toUserId\":200,\"content\":\"hello\",\"clientMsgId\":\"p-1\"}"
        ), 2);

        JsonNode authOk = OBJECT_MAPPER.readTree(received.get(0));
        JsonNode sendAck = OBJECT_MAPPER.readTree(received.get(1));

        assertThat(authOk.path("type").asText("")).isEqualTo("auth_ok");
        assertThat(authOk.path("workerId").asText("")).isEqualTo("worker-b");
        assertThat(sendAck.path("type").asText("")).isEqualTo("sendAck");
        assertThat(sendAck.path("cmd").asText("")).isEqualTo("sendPrivateText");
        assertThat(sendAck.path("clientMsgId").asText("")).isEqualTo("p-1");
        assertThat(sendAck.path("requestId").asText("")).startsWith("worker-b-");
    }

    private List<String> runSession(List<String> outboundFrames, int expectedMessages) throws Exception {
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();

        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
        URI gatewayUri = URI.create("ws://localhost:" + port + "/ws/im");

        Disposable sessionHandle = client.execute(gatewayUri, session -> {
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
            workerA = startWorker("worker-a");
        }
        return "ws://127.0.0.1:" + workerA.port() + "/internal/ws/im";
    }

    private static synchronized String workerAHttpUri() {
        if (workerA == null) {
            workerA = startWorker("worker-a");
        }
        return "http://127.0.0.1:" + workerA.port();
    }

    private static synchronized int workerAPort() {
        if (workerA == null) {
            workerA = startWorker("worker-a");
        }
        return workerA.port();
    }

    private static synchronized String workerBUri() {
        if (workerB == null) {
            workerB = startWorker("worker-b");
        }
        return "ws://127.0.0.1:" + workerB.port() + "/internal/ws/im";
    }

    private static synchronized String workerBHttpUri() {
        if (workerB == null) {
            workerB = startWorker("worker-b");
        }
        return "http://127.0.0.1:" + workerB.port();
    }

    private static synchronized int workerBPort() {
        if (workerB == null) {
            workerB = startWorker("worker-b");
        }
        return workerB.port();
    }

    private static DisposableServer startWorker(String workerId) {
        return HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.ws("/internal/ws/im", (in, out) ->
                        out.sendString(in.receive()
                                .asString()
                                .handle((text, sink) -> {
                                    JsonNode node = parse(text);
                                    String type = node.path("type").asText("");
                                    if ("auth".equals(type)) {
                                        sink.next("{\"type\":\"auth_ok\",\"workerId\":\"" + workerId + "\"}");
                                        return;
                                    }
                                    if ("sendPrivateText".equals(type)) {
                                        sink.next("{\"type\":\"sendAck\",\"cmd\":\"sendPrivateText\",\"clientMsgId\":\""
                                                + node.path("clientMsgId").asText("")
                                                + "\",\"requestId\":\"" + workerId + "-private-1\"}");
                                    }
                                }))
                ))
                .bindNow(Duration.ofSeconds(5));
    }

    private static JsonNode parse(String text) {
        try {
            return OBJECT_MAPPER.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static String userIdFor(String workerId) {
        ShardRouter router = new ConsistentHashShardRouter(new WorkerRegistry(List.of(
                worker("worker-a", workerAUri()),
                worker("worker-b", workerBUri())
        )));
        for (int candidate = 1; candidate <= 10_000; candidate++) {
            String userId = String.valueOf(candidate);
            if (router.route(userId).map(WorkerDescriptor::getId).filter(workerId::equals).isPresent()) {
                return userId;
            }
        }
        throw new IllegalStateException("No userId routed to " + workerId);
    }

    private static WorkerDescriptor worker(String id, String uri) {
        return new WorkerDescriptor(id, URI.create(uri));
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
