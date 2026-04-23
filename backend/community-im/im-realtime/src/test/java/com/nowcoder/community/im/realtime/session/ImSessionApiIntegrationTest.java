package com.nowcoder.community.im.realtime.session;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nowcoder.community.im.realtime.projection.ProjectionSyncCoordinator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "im.projection.bootstrap-on-startup=false",
        "im.session.worker-id=worker-a",
        "im.session.ws-base-url=ws://worker-a.example/internal/ws/im",
        "im.session.ticket-ttl=PT90S"
})
class ImSessionApiIntegrationTest {

    private enum SnapshotMode {
        UNAVAILABLE,
        EMPTY
    }

    private static final AtomicReference<SnapshotMode> IM_CORE_MODE = new AtomicReference<>(SnapshotMode.UNAVAILABLE);
    private static final AtomicReference<SnapshotMode> COMMUNITY_MODE = new AtomicReference<>(SnapshotMode.UNAVAILABLE);
    private static HttpServer imCoreServer;
    private static HttpServer communityServer;

    @LocalServerPort
    private int port;

    @Autowired
    private ProjectionSyncCoordinator projectionSyncCoordinator;

    @Value("${security.jwt.hmac-secret}")
    private String jwtSecret;

    @Value("${security.jwt.issuer}")
    private String jwtIssuer;

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        ensureServers();
        registry.add("spring.cloud.discovery.client.simple.instances.im-core[0].uri",
                () -> "http://127.0.0.1:" + imCoreServer.getAddress().getPort());
        registry.add("spring.cloud.discovery.client.simple.instances.community-app[0].uri",
                () -> "http://127.0.0.1:" + communityServer.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        IM_CORE_MODE.set(SnapshotMode.UNAVAILABLE);
        COMMUNITY_MODE.set(SnapshotMode.UNAVAILABLE);
    }

    @Test
    void postSessions_shouldReturnCodeZeroWithWorkerSpecificWsUrlAndTicket() throws Exception {
        IM_CORE_MODE.set(SnapshotMode.EMPTY);
        COMMUNITY_MODE.set(SnapshotMode.EMPTY);

        projectionSyncCoordinator.refreshNow().block(Duration.ofSeconds(5));

        client()
                .post()
                .uri("/api/im/sessions")
                .header(HttpHeaders.AUTHORIZATION, bearer(uuid(101)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data.sessionId").value(value -> assertThat(String.valueOf(value)).isNotBlank())
                .jsonPath("$.data.workerId").isEqualTo("worker-a")
                .jsonPath("$.data.wsUrl").isEqualTo("ws://worker-a.example/internal/ws/im")
                .jsonPath("$.data.ticket").value(value -> assertThat(String.valueOf(value)).isNotBlank())
                .jsonPath("$.data.expiresAtEpochMillis").value(value ->
                        assertThat(Long.parseLong(String.valueOf(value))).isGreaterThan(System.currentTimeMillis()));
    }

    @Test
    void readinessGate_shouldBlockSessionCreationUntilProjectionsAreLoaded() throws Exception {
        client()
                .post()
                .uri("/api/im/sessions")
                .header(HttpHeaders.AUTHORIZATION, bearer(uuid(202)))
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo(503);

        IM_CORE_MODE.set(SnapshotMode.EMPTY);
        COMMUNITY_MODE.set(SnapshotMode.EMPTY);

        projectionSyncCoordinator.refreshNow().block(Duration.ofSeconds(5));

        client()
                .post()
                .uri("/api/im/sessions")
                .header(HttpHeaders.AUTHORIZATION, bearer(uuid(202)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0);
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(5))
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(256 * 1024))
                        .build())
                .build();
    }

    private static synchronized void ensureServers() {
        if (imCoreServer != null && communityServer != null) {
            return;
        }
        try {
            if (imCoreServer == null) {
                imCoreServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                imCoreServer.createContext("/internal/im/realtime/projections/room-memberships",
                        ImSessionApiIntegrationTest::handleImCoreRequest);
                imCoreServer.start();
            }
            if (communityServer == null) {
                communityServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                communityServer.createContext("/internal/im/realtime/projections/user-policies",
                        ImSessionApiIntegrationTest::handleCommunityPoliciesRequest);
                communityServer.createContext("/internal/im/realtime/projections/block-relations",
                        ImSessionApiIntegrationTest::handleCommunityBlocksRequest);
                communityServer.start();
            }
        } catch (IOException e) {
            throw new RuntimeException("failed to start snapshot stub servers", e);
        }
    }

    private static void handleImCoreRequest(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (IM_CORE_MODE.get() == SnapshotMode.UNAVAILABLE) {
                writeJson(exchange, 503, "{\"message\":\"membership snapshot unavailable\"}");
                return;
            }
            writeJson(exchange, 200, "{\"entries\":[],\"nextRoomId\":null,\"nextUserId\":null,\"hasMore\":false}");
        }
    }

    private static void handleCommunityPoliciesRequest(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (COMMUNITY_MODE.get() == SnapshotMode.UNAVAILABLE) {
                writeJson(exchange, 503, "{\"message\":\"policy snapshot unavailable\"}");
                return;
            }
            writeJson(exchange, 200, "{\"entries\":[],\"nextUserId\":null,\"hasMore\":false}");
        }
    }

    private static void handleCommunityBlocksRequest(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (COMMUNITY_MODE.get() == SnapshotMode.UNAVAILABLE) {
                writeJson(exchange, 503, "{\"message\":\"block snapshot unavailable\"}");
                return;
            }
            writeJson(exchange, 200, "{\"entries\":[],\"nextBlockerUserId\":null,\"nextBlockedUserId\":null,\"hasMore\":false}");
        }
    }

    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private String bearer(UUID userId) throws Exception {
        return "Bearer " + signHs256(jwtSecret, jwtIssuer, userId.toString(), Instant.now().plusSeconds(120));
    }

    private static String signHs256(String secret, String issuer, String sub, Instant exp) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(sub)
                .issueTime(new Date())
                .expirationTime(Date.from(exp))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
