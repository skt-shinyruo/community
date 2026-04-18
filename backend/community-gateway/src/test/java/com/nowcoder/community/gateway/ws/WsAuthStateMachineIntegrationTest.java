package com.nowcoder.community.gateway.ws;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
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
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {
                CommunityGatewayApplication.class,
                WsAuthStateMachineIntegrationTest.PermitAllSecurityConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class WsAuthStateMachineIntegrationTest {

    private static final String JWT_SECRET = "gateway-test-jwt-secret-please-change-123456";
    private static final String JWT_ISSUER = "community-auth";

    private static volatile DisposableServer workerServer;

    @Autowired
    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.ws.proxy.path", () -> "/ws/im");
        registry.add("gateway.ws.proxy.auth-required", () -> true);
        registry.add("gateway.ws.proxy.default-worker-uri", WsAuthStateMachineIntegrationTest::workerUri);
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("security.jwt.hmac-secret", () -> JWT_SECRET);
        registry.add("security.jwt.issuer", () -> JWT_ISSUER);
    }

    @AfterAll
    static void tearDown() {
        if (workerServer != null) {
            workerServer.disposeNow();
            workerServer = null;
        }
    }

    @Test
    void shouldRejectNonAuthFirstFrame() throws Exception {
        String msg = runSession(session -> session.tryEmitNext("hello"));
        assertThat(msg).contains("\"type\":\"auth_error\"");
    }

    @Test
    void shouldRejectInvalidToken() throws Exception {
        String msg = runSession(session -> session.tryEmitNext("{\"type\":\"auth\",\"accessToken\":\"bad-token\"}"));
        assertThat(msg).contains("\"type\":\"auth_error\"");
        assertThat(msg).contains("invalid token");
    }

    @Test
    void shouldRejectTokenWithoutRequiredIssuer() throws Exception {
        String token = signHs256(JWT_SECRET, null, "123", Instant.now().plusSeconds(120));
        String msg = runSession(session -> session.tryEmitNext("{\"type\":\"auth\",\"accessToken\":\"" + token + "\"}"));
        assertThat(msg).contains("\"type\":\"auth_error\"");
        assertThat(msg).contains("invalid token");
    }

    @Test
    void shouldAcceptValidAuthAndProxySubsequentFrames() throws Exception {
        String token = signHs256(JWT_SECRET, JWT_ISSUER, "123", Instant.now().plusSeconds(120));
        String msg = runSession(session -> {
            session.tryEmitNext("{\"type\":\"auth\",\"accessToken\":\"" + token + "\"}");
            session.tryEmitNext("hello");
        });
        assertThat(msg).isEqualTo("worker:hello");
    }

    private String runSession(java.util.function.Consumer<Sinks.Many<String>> writer) throws Exception {
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
            writer.accept(outbound);
            outbound.tryEmitComplete();
            return received.poll(5, TimeUnit.SECONDS);
        } finally {
            ws.dispose();
        }
    }

    private static synchronized String workerUri() {
        if (workerServer == null) {
            workerServer = HttpServer.create()
                    .host("127.0.0.1")
                    .port(0)
                    .route(routes -> routes.ws("/internal/ws/im", (in, out) ->
                            out.sendString(in.receive()
                                    .asString()
                                    .filter(text -> !text.contains("\"type\":\"auth\""))
                                    .map(text -> "worker:" + text))
                    ))
                    .bindNow(Duration.ofSeconds(5));
        }
        return "ws://127.0.0.1:" + workerServer.port() + "/internal/ws/im";
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
