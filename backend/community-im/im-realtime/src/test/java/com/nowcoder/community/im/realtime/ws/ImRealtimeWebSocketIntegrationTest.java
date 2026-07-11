package com.nowcoder.community.im.realtime.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImTopics;
import com.nowcoder.community.im.common.event.UserMessagingPolicyChanged;
import com.nowcoder.community.im.common.projection.RoomMembershipEntry;
import com.nowcoder.community.im.common.projection.UserBlockRelationEntry;
import com.nowcoder.community.im.common.projection.UserMessagingPolicyEntry;
import com.nowcoder.community.im.common.ws.ConnectFrame;
import com.nowcoder.community.im.common.ws.SendPrivateTextFrame;
import com.nowcoder.community.im.realtime.presence.ConnectionRegistry;
import com.nowcoder.community.im.realtime.projection.PolicyProjectionService;
import com.nowcoder.community.im.realtime.projection.ProjectionSyncCoordinator;
import com.nowcoder.community.im.realtime.session.SessionTicketCodec;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@EmbeddedKafka(
        partitions = 1,
        topics = {
                ImTopics.COMMAND_PRIVATE_TEXT,
                ImTopics.EVENT_USER_MESSAGING_POLICY_CHANGED
        }
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "im.projection.bootstrap-on-startup=false",
        "im.session.worker-id=worker-a",
        "im.ws.path=/internal/ws/im",
        "im.ws.kafka-send-timeout-ms=1000"
})
class ImRealtimeWebSocketIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int APP_PORT = findAvailablePort();
    private static final String WORKER_ID = "worker-a";
    private static final String WS_PATH = "/internal/ws/im";
    private static final long POLICY_OWNER_VERSION = 1L;

    private static final AtomicReference<List<RoomMembershipEntry>> MEMBERSHIP_ENTRIES = new AtomicReference<>(List.of());
    private static final AtomicReference<List<UserMessagingPolicyEntry>> POLICY_ENTRIES = new AtomicReference<>(List.of());
    private static final AtomicReference<List<UserBlockRelationEntry>> BLOCK_ENTRIES = new AtomicReference<>(List.of());

    private static HttpServer imCoreServer;
    private static HttpServer communityServer;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectionSyncCoordinator projectionSyncCoordinator;

    @Autowired
    private PolicyProjectionService policyProjectionService;

    @Autowired
    private ConnectionRegistry connectionRegistry;

    @Autowired
    private SessionTicketCodec sessionTicketCodec;

    private Consumer<String, String> commandConsumer;

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        ensureSnapshotServers();
        registry.add("server.port", () -> APP_PORT);
        registry.add("spring.cloud.discovery.client.simple.instances.im-core[0].uri",
                () -> "http://127.0.0.1:" + imCoreServer.getAddress().getPort());
        registry.add("spring.cloud.discovery.client.simple.instances.community-app[0].uri",
                () -> "http://127.0.0.1:" + communityServer.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        MEMBERSHIP_ENTRIES.set(List.of());
        POLICY_ENTRIES.set(List.of());
        BLOCK_ENTRIES.set(List.of());
        if (commandConsumer != null) {
            try {
                commandConsumer.close();
            } catch (RuntimeException ignore) {
            }
        }
    }

    @Test
    void websocket_shouldAcceptGatewayIssuedTicketOnInternalWorkerPath_andApplyLocalPrivatePolicyProjection() throws Exception {
        UUID senderUserId = uuid(100);
        UUID allowedRecipientId = uuid(200);
        UUID deniedRecipientId = uuid(201);

        MEMBERSHIP_ENTRIES.set(List.of());
        POLICY_ENTRIES.set(List.of(
                policy(senderUserId, true),
                policy(allowedRecipientId, true),
                policy(deniedRecipientId, true)
        ));
        BLOCK_ENTRIES.set(List.of());

        projectionSyncCoordinator.refreshNow().block(Duration.ofSeconds(5));

        commandConsumer = newCommandStringConsumer("im-realtime-ws-it");
        commandConsumer.subscribe(List.of(ImTopics.COMMAND_PRIVATE_TEXT));
        commandConsumer.poll(Duration.ofMillis(200));

        OpenSessionData sessionData = newSession(senderUserId, WORKER_ID);
        assertThat(sessionData.wsUrl()).isNotBlank();
        assertThat(sessionData.ticket()).isNotBlank();
        assertThat(sessionData.sessionId()).isNotBlank();

        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Empty<Void> done = Sinks.empty();
        CountDownLatch connected = new CountDownLatch(1);

        Disposable websocket = openWebSocket(sessionData.wsUrl(), received, outbound, done, connected);
        try {
            assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();

            outbound.tryEmitNext(json(new ConnectFrame("connect", sessionData.ticket())));

            JsonNode connectedFrame = awaitType(received, "connected", Duration.ofSeconds(5));
            assertThat(connectedFrame.path("sessionId").asText("")).isEqualTo(sessionData.sessionId());

            outbound.tryEmitNext(json(new SendPrivateTextFrame("sendPrivateText", "c-allow", allowedRecipientId, "hi")));

            JsonNode ackFrame = awaitType(received, "ack", Duration.ofSeconds(5));
            assertThat(ackFrame.path("cmd").asText("")).isEqualTo("sendPrivateText");
            assertThat(ackFrame.path("clientMsgId").asText("")).isEqualTo("c-allow");
            assertThat(ackFrame.path("requestId").asText("")).isNotBlank();

            ConsumerRecord<String, String> commandRecord =
                    pollForSingleRecord(commandConsumer, ImTopics.COMMAND_PRIVATE_TEXT, Duration.ofSeconds(10));
            JsonNode command = objectMapper.readTree(commandRecord.value());
            assertThat(command.path("fromUserId").asText("")).isEqualTo(senderUserId.toString());
            assertThat(command.path("toUserId").asText("")).isEqualTo(allowedRecipientId.toString());
            assertThat(command.path("content").asText("")).isEqualTo("hi");
            assertThat(command.path("clientMsgId").asText("")).isEqualTo("c-allow");

            awaitRealtimeEventAssignments(Set.of(ImTopics.EVENT_USER_MESSAGING_POLICY_CHANGED), Duration.ofSeconds(8));

            kafkaTemplate.send(
                    ImTopics.EVENT_USER_MESSAGING_POLICY_CHANGED,
                    deniedRecipientId.toString(),
                    new UserMessagingPolicyChanged(
                            "evt-policy-deny",
                            deniedRecipientId,
                            true,
                            false,
                            false,
                            null,
                            null,
                            false,
                            System.currentTimeMillis(),
                            2L
                    )
            ).get(5, TimeUnit.SECONDS);

            awaitPrivatePolicyDenied(senderUserId, deniedRecipientId, Duration.ofSeconds(5));

            outbound.tryEmitNext(json(new SendPrivateTextFrame("sendPrivateText", "c-deny", deniedRecipientId, "blocked")));

            JsonNode rejectFrame = awaitType(received, "reject", Duration.ofSeconds(5));
            assertThat(rejectFrame.path("cmd").asText("")).isEqualTo("sendPrivateText");
            assertThat(rejectFrame.path("clientMsgId").asText("")).isEqualTo("c-deny");
            assertThat(rejectFrame.path("reasonCode").asText("")).isEqualTo("policy_denied");
            assertThat(rejectFrame.path("requestId").asText("")).isNotBlank();
        } finally {
            done.tryEmitEmpty();
            outbound.tryEmitComplete();
            if (websocket != null) {
                websocket.dispose();
            }
        }
    }

    @Test
    void websocket_shouldRejectTicketIssuedForAnotherWorker() throws Exception {
        UUID userId = uuid(300);
        MEMBERSHIP_ENTRIES.set(List.of());
        POLICY_ENTRIES.set(List.of(policy(userId, true)));
        BLOCK_ENTRIES.set(List.of());

        projectionSyncCoordinator.refreshNow().block(Duration.ofSeconds(5));

        OpenSessionData sessionData = newSession(userId, "worker-b");
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Empty<Void> done = Sinks.empty();
        CountDownLatch connected = new CountDownLatch(1);

        Disposable websocket = openWebSocket(sessionData.wsUrl(), received, outbound, done, connected);
        try {
            assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();

            outbound.tryEmitNext(json(new ConnectFrame("connect", sessionData.ticket())));

            JsonNode rejectFrame = awaitType(received, "reject", Duration.ofSeconds(5));
            assertThat(rejectFrame.path("cmd").asText("")).isEqualTo("connect");
            assertThat(rejectFrame.path("code").asInt()).isEqualTo(403);
            assertThat(rejectFrame.path("reasonCode").asText("")).isEqualTo("wrong_worker");
        } finally {
            done.tryEmitEmpty();
            outbound.tryEmitComplete();
            if (websocket != null) {
                websocket.dispose();
            }
        }
    }

    @Test
    void websocket_shouldRejectMissingSchemaVersionBeforeBindingSession() throws Exception {
        UUID userId = uuid(303);
        MEMBERSHIP_ENTRIES.set(List.of());
        POLICY_ENTRIES.set(List.of(policy(userId, true)));
        BLOCK_ENTRIES.set(List.of());

        projectionSyncCoordinator.refreshNow().block(Duration.ofSeconds(5));

        OpenSessionData sessionData = newSession(userId, WORKER_ID);
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Empty<Void> done = Sinks.empty();
        CountDownLatch connected = new CountDownLatch(1);

        Disposable websocket = openWebSocket(sessionData.wsUrl(), received, outbound, done, connected);
        try {
            assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();

            outbound.tryEmitNext(JSON.writeValueAsString(Map.of(
                    "type", "connect",
                    "ticket", sessionData.ticket()
            )));

            JsonNode rejectFrame = awaitType(received, "reject", Duration.ofSeconds(5));
            assertThat(rejectFrame.path("cmd").asText("")).isEqualTo("protocol");
            assertThat(rejectFrame.path("reasonCode").asText(""))
                    .isEqualTo("unsupported_schema_version");
            assertThat(connectionRegistry.listByUserId(userId)).isEmpty();
        } finally {
            done.tryEmitEmpty();
            outbound.tryEmitComplete();
            if (websocket != null) {
                websocket.dispose();
            }
        }
    }

    @Test
    void websocket_shouldRejectUnsupportedFutureSchemaVersionAsProtocolError() throws Exception {
        UUID userId = uuid(301);
        MEMBERSHIP_ENTRIES.set(List.of());
        POLICY_ENTRIES.set(List.of(policy(userId, true), policy(uuid(302), true)));
        BLOCK_ENTRIES.set(List.of());

        projectionSyncCoordinator.refreshNow().block(Duration.ofSeconds(5));

        OpenSessionData sessionData = newSession(userId, WORKER_ID);
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Empty<Void> done = Sinks.empty();
        CountDownLatch connected = new CountDownLatch(1);

        Disposable websocket = openWebSocket(sessionData.wsUrl(), received, outbound, done, connected);
        try {
            assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();

            outbound.tryEmitNext(json(new ConnectFrame("connect", sessionData.ticket())));
            assertThat(awaitType(received, "connected", Duration.ofSeconds(5))
                    .path("sessionId").asText("")).isEqualTo(sessionData.sessionId());

            outbound.tryEmitNext("""
                    {
                      "type": "sendPrivateText",
                      "schemaVersion": 2,
                      "clientMsgId": "c-future-frame",
                      "toUserId": "00000000-0000-7000-8000-00000000012e",
                      "content": "hi from the future"
                    }
                    """);

            JsonNode rejectFrame = awaitType(received, "reject", Duration.ofSeconds(5));
            assertThat(rejectFrame.path("cmd").asText("")).isEqualTo("protocol");
            assertThat(rejectFrame.path("code").asInt()).isEqualTo(400);
            assertThat(rejectFrame.path("reasonCode").asText("")).isEqualTo("unsupported_schema_version");
        } finally {
            done.tryEmitEmpty();
            outbound.tryEmitComplete();
            if (websocket != null) {
                websocket.dispose();
            }
        }
    }

    private Disposable openWebSocket(
            String wsUrl,
            LinkedBlockingQueue<String> received,
            Sinks.Many<String> outbound,
            Sinks.Empty<Void> done,
            CountDownLatch connected
    ) {
        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
        return client.execute(URI.create(wsUrl), session -> {
                    connected.countDown();

                    Mono<Void> send = session.send(outbound.asFlux().map(session::textMessage));
                    Mono<Void> recv = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .doOnNext(received::offer)
                            .then();
                    Mono<Void> closer = done.asMono().then(session.close());
                    return Mono.when(send, recv, closer);
                })
                .subscribe();
    }

    private Consumer<String, String> newCommandStringConsumer(String groupId) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(groupId, "true", embeddedKafka);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        DefaultKafkaConsumerFactory<String, String> factory = new DefaultKafkaConsumerFactory<>(props);
        return factory.createConsumer();
    }

    private JsonNode awaitType(LinkedBlockingQueue<String> received, String type, Duration timeout) throws Exception {
        long deadlineMs = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadlineMs) {
            long waitMs = Math.max(1L, deadlineMs - System.currentTimeMillis());
            String message = received.poll(waitMs, TimeUnit.MILLISECONDS);
            if (message == null) {
                continue;
            }
            JsonNode node = objectMapper.readTree(message);
            if (type.equals(node.path("type").asText(""))) {
                return node;
            }
        }
        throw new AssertionError("Timed out waiting for websocket message type=" + type);
    }

    private void awaitRealtimeEventAssignments(Set<String> expectedTopics, Duration timeout) throws Exception {
        long deadlineMs = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadlineMs) {
            Set<String> assignedTopics = new java.util.HashSet<>();
            for (MessageListenerContainer container : kafkaListenerEndpointRegistry.getListenerContainers()) {
                if (container == null || !container.isRunning()) {
                    continue;
                }
                var partitions = container.getAssignedPartitions();
                if (partitions == null || partitions.isEmpty()) {
                    continue;
                }
                for (TopicPartition partition : partitions) {
                    if (partition != null && partition.topic() != null) {
                        assignedTopics.add(partition.topic());
                    }
                }
            }
            if (assignedTopics.containsAll(expectedTopics)) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("Timed out waiting for realtime Kafka assignments: " + expectedTopics);
    }

    private void awaitPrivatePolicyDenied(UUID fromUserId, UUID toUserId, Duration timeout) throws Exception {
        long deadlineMs = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadlineMs) {
            if (!policyProjectionService.canSendPrivate(fromUserId, toUserId).allowed()) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("Timed out waiting for private policy denial");
    }

    private static ConsumerRecord<String, String> pollForSingleRecord(
            Consumer<String, String> consumer,
            String topic,
            Duration timeout
    ) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
            if (records == null || records.isEmpty()) {
                continue;
            }
            Iterable<ConsumerRecord<String, String>> iterable = records.records(topic);
            if (iterable != null) {
                for (ConsumerRecord<String, String> record : iterable) {
                    return record;
                }
            }
        }
        throw new AssertionError("Timed out waiting for record on topic " + topic);
    }

    private static String json(Object value) throws Exception {
        return JSON.writeValueAsString(value);
    }

    private static synchronized void ensureSnapshotServers() {
        if (imCoreServer != null && communityServer != null) {
            return;
        }
        try {
            if (imCoreServer == null) {
                imCoreServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                imCoreServer.createContext(
                        "/internal/im/realtime/projections/room-memberships",
                        ImRealtimeWebSocketIntegrationTest::handleMembershipSnapshot
                );
                imCoreServer.start();
            }
            if (communityServer == null) {
                communityServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                communityServer.createContext(
                        "/internal/im/realtime/projections/user-policies",
                        ImRealtimeWebSocketIntegrationTest::handlePolicySnapshot
                );
                communityServer.createContext(
                        "/internal/im/realtime/projections/block-relations",
                        ImRealtimeWebSocketIntegrationTest::handleBlockSnapshot
                );
                communityServer.start();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start snapshot stubs", e);
        }
    }

    private static void handleMembershipSnapshot(HttpExchange exchange) throws IOException {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("schemaVersion", ImContractVersions.PROJECTION_SCHEMA_VERSION);
        body.put("entries", MEMBERSHIP_ENTRIES.get());
        body.put("nextRoomId", null);
        body.put("nextUserId", null);
        body.put("hasMore", false);
        body.put("snapshotHighWatermark", 0L);
        writeJson(exchange, 200, body);
    }

    private static void handlePolicySnapshot(HttpExchange exchange) throws IOException {
        List<UserMessagingPolicyEntry> entries = POLICY_ENTRIES.get();
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("schemaVersion", ImContractVersions.PROJECTION_SCHEMA_VERSION);
        body.put("entries", entries);
        body.put("nextUserId", null);
        body.put("hasMore", false);
        body.put("snapshotHighWatermark", entries.isEmpty() ? 0L : POLICY_OWNER_VERSION);
        writeJson(exchange, 200, body);
    }

    private static void handleBlockSnapshot(HttpExchange exchange) throws IOException {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("schemaVersion", ImContractVersions.PROJECTION_SCHEMA_VERSION);
        body.put("entries", BLOCK_ENTRIES.get());
        body.put("nextBlockerUserId", null);
        body.put("nextBlockedUserId", null);
        body.put("hasMore", false);
        body.put("snapshotHighWatermark", 0L);
        writeJson(exchange, 200, body);
    }

    private static void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
        try (exchange) {
            byte[] bytes = JSON.writeValueAsBytes(body);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        }
    }

    private static UserMessagingPolicyEntry policy(UUID userId, boolean canSendPrivate) {
        return new UserMessagingPolicyEntry(
                userId,
                true,
                false,
                false,
                null,
                null,
                canSendPrivate,
                POLICY_OWNER_VERSION,
                null
        );
    }

    private OpenSessionData newSession(UUID userId, String workerId) {
        String sessionId = UUID.randomUUID().toString();
        String ticket = sessionTicketCodec.encode(sessionId, userId, workerId, Instant.now().plusSeconds(120));
        return new OpenSessionData(sessionId, "ws://127.0.0.1:" + APP_PORT + WS_PATH, ticket);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to allocate test port", e);
        }
    }

    private record OpenSessionData(String sessionId, String wsUrl, String ticket) {
    }
}
