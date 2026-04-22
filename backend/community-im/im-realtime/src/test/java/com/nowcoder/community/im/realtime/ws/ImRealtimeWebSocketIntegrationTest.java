package com.nowcoder.community.im.realtime.ws;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nowcoder.community.im.common.ImTopics;
import com.nowcoder.community.im.common.command.SendPrivateTextCommandV1;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEventV1;
import com.nowcoder.community.im.common.event.PrivateMessageRejectedEventV1;
import com.nowcoder.community.im.common.event.RoomMemberChangedEventV1;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEventV1;
import com.nowcoder.community.im.common.event.RoomMessageRejectedEventV1;
import com.nowcoder.community.im.realtime.client.CommunityGovernanceClient;
import com.nowcoder.community.im.realtime.kafka.CommandProducer;
import com.nowcoder.community.im.realtime.presence.ConnectionRegistry;
import com.nowcoder.community.im.realtime.presence.RoomLocalIndex;
import com.nowcoder.community.im.realtime.presence.WsConnection;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.WebSocketMessage;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {
                ImTopics.COMMAND_PRIVATE_TEXT_V1,
                ImTopics.COMMAND_ROOM_TEXT_V1,
                ImTopics.EVENT_PRIVATE_PERSISTED_V1,
                ImTopics.EVENT_ROOM_PERSISTED_V1,
                ImTopics.EVENT_PRIVATE_REJECTED_V1,
                ImTopics.EVENT_ROOM_REJECTED_V1,
                ImTopics.EVENT_ROOM_MEMBER_CHANGED_V1
        }
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.cloud.nacos.discovery.enabled=false",
        "im.ws.room-flush-interval-ms=10",
        "im.ws.kafka-send-timeout-ms=1000"
})
class ImRealtimeWebSocketIntegrationTest {

    private enum ImCoreBootstrapMode {
        EMPTY,
        FAILURE
    }

    private static final AtomicReference<ImCoreBootstrapMode> IM_CORE_BOOTSTRAP_MODE = new AtomicReference<>(ImCoreBootstrapMode.EMPTY);
    private static final LinkedBlockingQueue<Map<String, String>> IM_CORE_REQUEST_HEADERS = new LinkedBlockingQueue<>();
    private static HttpServer imCoreServer;

    @LocalServerPort
    private int port;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConnectionRegistry connectionRegistry;

    @Autowired
    private RoomLocalIndex roomLocalIndex;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @MockBean
    private CommunityGovernanceClient governanceClient;

    @SpyBean
    private CommandProducer commandProducer;

    @Value("${security.jwt.hmac-secret}")
    private String jwtSecret;

    @Value("${security.jwt.issuer}")
    private String jwtIssuer;

    private Consumer<String, String> commandConsumer;

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        ensureImCoreServer();
        registry.add("spring.cloud.discovery.client.simple.instances.im-core[0].uri",
                () -> "http://127.0.0.1:" + imCoreServer.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        if (commandConsumer != null) {
            try {
                commandConsumer.close();
            } catch (RuntimeException ignore) {
            }
        }
        IM_CORE_BOOTSTRAP_MODE.set(ImCoreBootstrapMode.EMPTY);
        IM_CORE_REQUEST_HEADERS.clear();
    }

    @Test
    void websocket_shouldAuth_sendCommands_andReceiveAcceptedCommittedAndPushFrames() throws Exception {
        when(governanceClient.validateSendPrivateMessage(anyString(), any(), anyString()))
                .thenReturn(Mono.just(CommunityGovernanceClient.Decision.allow("")));

        UUID userId = uuid(100);
        UUID toUserId = uuid(200);
        UUID roomId = uuid(10);
        String conversationId = conversationId(userId, toUserId);

        commandConsumer = newCommandStringConsumer("im-realtime-it-commands");
        commandConsumer.subscribe(List.of(ImTopics.COMMAND_PRIVATE_TEXT_V1, ImTopics.COMMAND_ROOM_TEXT_V1));
        commandConsumer.poll(Duration.ofMillis(200));

        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Empty<Void> done = Sinks.empty();

        CountDownLatch connected = new CountDownLatch(1);

        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
        URI uri = URI.create("ws://localhost:" + port + "/ws/im");

        Disposable ws = client.execute(uri, session -> {
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

        try {
            assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();

            String token = signHs256(jwtSecret, jwtIssuer, userId.toString(), Instant.now().plusSeconds(120));
            outbound.tryEmitNext("{\"type\":\"auth\",\"accessToken\":\"" + token + "\"}");

            JsonNode authOk = awaitType(received, "auth_ok", Duration.ofSeconds(5));
            assertThat(authOk.path("userId").asText("")).isEqualTo(userId.toString());

            awaitRealtimeEventAssignments(
                    Set.of(
                            ImTopics.EVENT_PRIVATE_PERSISTED_V1,
                            ImTopics.EVENT_ROOM_PERSISTED_V1,
                            ImTopics.EVENT_PRIVATE_REJECTED_V1,
                            ImTopics.EVENT_ROOM_REJECTED_V1,
                            ImTopics.EVENT_ROOM_MEMBER_CHANGED_V1
                    ),
                    Duration.ofSeconds(8)
            );

            outbound.tryEmitNext("{\"type\":\"sendPrivateText\",\"clientMsgId\":\"c1\",\"toUserId\":\"" + toUserId + "\",\"content\":\"hi\"}");
            JsonNode privateAccepted = awaitType(received, "sendAccepted", Duration.ofSeconds(5));
            assertThat(privateAccepted.path("cmd").asText("")).isEqualTo("sendPrivateText");
            assertThat(privateAccepted.path("clientMsgId").asText("")).isEqualTo("c1");
            ConsumerRecord<String, String> privateCmdRecord = pollForSingleRecord(commandConsumer, ImTopics.COMMAND_PRIVATE_TEXT_V1, Duration.ofSeconds(10));
            JsonNode privateCmd = objectMapper.readTree(privateCmdRecord.value());
            assertThat(privateCmd.path("fromUserId").asText("")).isEqualTo(userId.toString());
            assertThat(privateCmd.path("toUserId").asText("")).isEqualTo(toUserId.toString());
            assertThat(privateCmd.path("conversationId").asText("")).isEqualTo(conversationId);
            assertThat(privateCmd.path("content").asText("")).isEqualTo("hi");
            assertThat(privateCmd.path("clientMsgId").asText("")).isEqualTo("c1");

            outbound.tryEmitNext("{\"type\":\"sendRoomText\",\"clientMsgId\":\"c2\",\"roomId\":\"" + roomId + "\",\"content\":\"hello\"}");
            JsonNode roomAccepted = awaitType(received, "sendAccepted", Duration.ofSeconds(5));
            assertThat(roomAccepted.path("cmd").asText("")).isEqualTo("sendRoomText");
            assertThat(roomAccepted.path("clientMsgId").asText("")).isEqualTo("c2");
            ConsumerRecord<String, String> roomCmdRecord = pollForSingleRecord(commandConsumer, ImTopics.COMMAND_ROOM_TEXT_V1, Duration.ofSeconds(10));
            JsonNode roomCmd = objectMapper.readTree(roomCmdRecord.value());
            assertThat(roomCmd.path("fromUserId").asText("")).isEqualTo(userId.toString());
            assertThat(roomCmd.path("roomId").asText("")).isEqualTo(roomId.toString());
            assertThat(roomCmd.path("content").asText("")).isEqualTo("hello");
            assertThat(roomCmd.path("clientMsgId").asText("")).isEqualTo("c2");

            kafkaTemplate.send(
                    ImTopics.EVENT_ROOM_MEMBER_CHANGED_V1,
                    String.valueOf(roomId),
                    new RoomMemberChangedEventV1("evt-join", roomId, userId, "JOINED", System.currentTimeMillis())
            ).get(5, TimeUnit.SECONDS);

            awaitRoomJoinedInProcess(userId, roomId, Duration.ofSeconds(3));

            kafkaTemplate.send(
                    ImTopics.EVENT_ROOM_PERSISTED_V1,
                    String.valueOf(roomId),
                    new RoomMessagePersistedEventV1("evt-room", roomId, 7L, uuid(9001), userId, "req-room-1", "c2", System.currentTimeMillis())
            ).get(5, TimeUnit.SECONDS);

            JsonNode roomCommitted = awaitType(received, "sendCommitted", Duration.ofSeconds(5));
            assertThat(roomCommitted.path("cmd").asText("")).isEqualTo("sendRoomText");
            assertThat(roomCommitted.path("clientMsgId").asText("")).isEqualTo("c2");
            assertThat(roomCommitted.path("roomId").asText("")).isEqualTo(roomId.toString());
            assertThat(roomCommitted.path("seq").asLong()).isEqualTo(7L);

            JsonNode roomUpdated = awaitType(received, "roomUpdatedBatch", Duration.ofSeconds(5));
            assertThat(roomUpdated.hasNonNull("content")).isFalse();
            assertThat(roomUpdated.path("items").isArray()).isTrue();
            boolean match = false;
            for (JsonNode item : roomUpdated.path("items")) {
                if (roomId.toString().equals(item.path("roomId").asText("")) && item.path("lastSeq").asLong() == 7L) {
                    match = true;
                    break;
                }
            }
            assertThat(match).isTrue();

            kafkaTemplate.send(
                    ImTopics.EVENT_PRIVATE_PERSISTED_V1,
                    conversationId,
                    new PrivateMessagePersistedEventV1(
                            "evt-private",
                            conversationId,
                            3L,
                            uuid(12345),
                            userId,
                            toUserId,
                            "server-hi",
                            "req-private-1",
                            "c1",
                            System.currentTimeMillis()
                    )
            );

            JsonNode privateCommitted = awaitType(received, "sendCommitted", Duration.ofSeconds(5));
            assertThat(privateCommitted.path("cmd").asText("")).isEqualTo("sendPrivateText");
            assertThat(privateCommitted.path("clientMsgId").asText("")).isEqualTo("c1");
            assertThat(privateCommitted.path("conversationId").asText("")).isEqualTo(conversationId);
            assertThat(privateCommitted.path("seq").asLong()).isEqualTo(3L);

            JsonNode privateMsg = awaitType(received, "privateMessage", Duration.ofSeconds(5));
            assertThat(privateMsg.path("conversationId").asText("")).isEqualTo(conversationId);
            assertThat(privateMsg.path("fromUserId").asText("")).isEqualTo(userId.toString());
            assertThat(privateMsg.path("toUserId").asText("")).isEqualTo(toUserId.toString());
            assertThat(privateMsg.path("content").asText("")).isEqualTo("server-hi");
        } finally {
            done.tryEmitEmpty();
            outbound.tryEmitComplete();
        }
    }

    @Test
    void websocket_shouldReceiveSendRejectedWhenImCoreRejectsAcceptedRoomCommand() throws Exception {
        UUID userId = uuid(100);
        UUID roomId = uuid(10);

        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Empty<Void> done = Sinks.empty();
        CountDownLatch connected = new CountDownLatch(1);

        Disposable ws = openWebSocket(received, outbound, done, connected);
        try {
            assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();

            String token = signHs256(jwtSecret, jwtIssuer, userId.toString(), Instant.now().plusSeconds(120));
            outbound.tryEmitNext("{\"type\":\"auth\",\"accessToken\":\"" + token + "\"}");
            JsonNode authOk = awaitType(received, "auth_ok", Duration.ofSeconds(5));
            assertThat(authOk.path("userId").asText("")).isEqualTo(userId.toString());

            awaitRealtimeEventAssignments(Set.of(ImTopics.EVENT_ROOM_REJECTED_V1), Duration.ofSeconds(8));

            kafkaTemplate.send(
                    ImTopics.EVENT_ROOM_REJECTED_V1,
                    String.valueOf(roomId),
                    new RoomMessageRejectedEventV1(
                            "evt-room-rejected",
                            "req-room-rejected",
                            "c-room-rejected",
                            userId,
                            roomId,
                            403,
                            "not_room_member",
                            "not a room member",
                            System.currentTimeMillis()
                    )
            ).get(5, TimeUnit.SECONDS);

            JsonNode rejected = awaitType(received, "sendRejected", Duration.ofSeconds(5));
            assertThat(rejected.path("cmd").asText("")).isEqualTo("sendRoomText");
            assertThat(rejected.path("clientMsgId").asText("")).isEqualTo("c-room-rejected");
            assertThat(rejected.path("requestId").asText("")).isEqualTo("req-room-rejected");
            assertThat(rejected.path("code").asInt()).isEqualTo(403);
            assertThat(rejected.path("reasonCode").asText("")).isEqualTo("not_room_member");
            assertThat(rejected.path("message").asText("")).isEqualTo("not a room member");
        } finally {
            done.tryEmitEmpty();
            outbound.tryEmitComplete();
        }
    }

    @Test
    void websocket_shouldLogAuthDeniedWhenTokenIsInvalid() throws Exception {
        String headerTraceId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String expectedTraceId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        ListAppender<ILoggingEvent> logs = startWsLogCapture();
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Empty<Void> done = Sinks.empty();
        CountDownLatch connected = new CountDownLatch(1);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Id", headerTraceId);
        headers.set("traceparent", "00-" + expectedTraceId + "-1234567890abcdef-01");

        Disposable ws = openWebSocket(received, outbound, done, connected, headers);
        try {
            assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();

            outbound.tryEmitNext("{\"type\":\"auth\",\"accessToken\":\"invalid.jwt.token\"}");

            JsonNode authError = awaitType(received, "auth_error", Duration.ofSeconds(5));
            assertThat(authError.path("message").asText("")).isEqualTo("invalid token");

            ILoggingEvent authEvent = awaitLogEvent(logs, "community.action=ws_auth", Duration.ofSeconds(5));
            assertThat(authEvent.getThrowableProxy()).isNull();
            assertThat(authEvent.getFormattedMessage())
                    .contains("community.category=security")
                    .contains("community.action=ws_auth")
                    .contains("community.outcome=denied")
                    .contains("community.reason_code=invalid_token")
                    .contains("community.error_class=org.springframework.security.oauth2.jwt.BadJwtException")
                    .contains("community.error_message=")
                    .doesNotContain("\n")
                    .doesNotContain("invalid.jwt.token");
            assertThat(authEvent.getMDCPropertyMap()).containsEntry("traceId", expectedTraceId);
        } finally {
            done.tryEmitEmpty();
            outbound.tryEmitComplete();
            stopWsLogCapture(logs);
        }
    }

    @Test
    void websocket_shouldLogBootstrapFailureAndDisconnectSummary() throws Exception {
        IM_CORE_BOOTSTRAP_MODE.set(ImCoreBootstrapMode.FAILURE);
        ListAppender<ILoggingEvent> logs = startWsLogCapture();

        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Empty<Void> done = Sinks.empty();
        CountDownLatch connected = new CountDownLatch(1);

        Disposable ws = openWebSocket(received, outbound, done, connected);
        try {
            assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();

            UUID userId = uuid(100);
            String token = signHs256(jwtSecret, jwtIssuer, userId.toString(), Instant.now().plusSeconds(120));
            outbound.tryEmitNext("{\"type\":\"auth\",\"accessToken\":\"" + token + "\"}");

            JsonNode authOk = awaitType(received, "auth_ok", Duration.ofSeconds(5));
            assertThat(authOk.path("userId").asText("")).isEqualTo(userId.toString());

            ILoggingEvent bootstrapEvent = awaitLogEvent(logs, "community.action=ws_room_bootstrap", Duration.ofSeconds(5));
            assertThat(bootstrapEvent.getThrowableProxy()).isNull();
            assertThat(bootstrapEvent.getFormattedMessage())
                    .contains("community.category=integration")
                    .contains("community.error_class=org.springframework.web.reactive.function.client.WebClientResponseException$ServiceUnavailable")
                    .contains("community.error_message=")
                    .doesNotContain("\n");
        } finally {
            done.tryEmitEmpty();
            outbound.tryEmitComplete();
        }

        try {
            awaitLogContains(logs, "community.action=ws_disconnect", Duration.ofSeconds(5));
            assertThat(logOutput(logs))
                    .contains("community.category=integration")
                    .contains("community.action=ws_room_bootstrap")
                    .contains("community.outcome=degraded")
                    .contains("community.reason_code=bootstrap_failed")
                    .contains("user.id=" + uuid(100))
                    .contains("community.category=access")
                    .contains("community.action=ws_disconnect")
                    .contains("community.outcome=success")
                    .contains("community.joined_room_count=0");
        } finally {
            stopWsLogCapture(logs);
        }
    }

    @Test
    void websocket_shouldLogKafkaSendFailureWithoutMessageContent() throws Exception {
        when(governanceClient.validateSendPrivateMessage(anyString(), any(), anyString()))
                .thenReturn(Mono.just(CommunityGovernanceClient.Decision.allow("")));
        doReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")))
                .when(commandProducer).sendPrivateText(any(SendPrivateTextCommandV1.class));
        ListAppender<ILoggingEvent> logs = startWsLogCapture();

        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Empty<Void> done = Sinks.empty();
        CountDownLatch connected = new CountDownLatch(1);

        Disposable ws = openWebSocket(received, outbound, done, connected);
        try {
            assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();

            UUID userId = uuid(100);
            UUID toUserId = uuid(200);
            String token = signHs256(jwtSecret, jwtIssuer, userId.toString(), Instant.now().plusSeconds(120));
            outbound.tryEmitNext("{\"type\":\"auth\",\"accessToken\":\"" + token + "\"}");
            JsonNode authOk = awaitType(received, "auth_ok", Duration.ofSeconds(5));
            assertThat(authOk.path("userId").asText("")).isEqualTo(userId.toString());

            outbound.tryEmitNext("{\"type\":\"sendPrivateText\",\"clientMsgId\":\"c-fail\",\"toUserId\":\"" + toUserId + "\",\"content\":\"secret-payload\"}");

            JsonNode sendError = awaitType(received, "sendError", Duration.ofSeconds(5));
            assertThat(sendError.path("cmd").asText("")).isEqualTo("sendPrivateText");
            assertThat(sendError.path("clientMsgId").asText("")).isEqualTo("c-fail");

            ILoggingEvent enqueueEvent = awaitLogEvent(logs, "community.action=ws_command_enqueue", Duration.ofSeconds(5));
            assertThat(enqueueEvent.getThrowableProxy()).isNull();
            assertThat(enqueueEvent.getFormattedMessage())
                    .contains("community.category=integration")
                    .contains("community.action=ws_command_enqueue")
                    .contains("community.outcome=failure")
                    .contains("community.reason_code=kafka_send_failed")
                    .contains("user.id=" + userId)
                    .contains("community.command=sendPrivateText")
                    .contains("community.client_msg_id=c-fail")
                    .contains("community.error_class=java.lang.RuntimeException")
                    .contains("community.error_message=broker%20unavailable")
                    .doesNotContain("\n")
                    .doesNotContain("secret-payload");
        } finally {
            done.tryEmitEmpty();
            outbound.tryEmitComplete();
            stopWsLogCapture(logs);
        }
    }

    @Test
    void websocket_shouldForwardHandshakeTraceHeadersToImCoreBootstrapAndDisconnectLog() throws Exception {
        String headerTraceId = "cccccccccccccccccccccccccccccccc";
        String expectedTraceId = "dddddddddddddddddddddddddddddddd";
        ListAppender<ILoggingEvent> logs = startWsLogCapture();

        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Empty<Void> done = Sinks.empty();
        CountDownLatch connected = new CountDownLatch(1);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Id", headerTraceId);
        headers.set("traceparent", "00-" + expectedTraceId + "-fedcba0987654321-01");

        Disposable ws = openWebSocket(received, outbound, done, connected, headers);
        try {
            assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();

            UUID userId = uuid(100);
            String token = signHs256(jwtSecret, jwtIssuer, userId.toString(), Instant.now().plusSeconds(120));
            outbound.tryEmitNext("{\"type\":\"auth\",\"accessToken\":\"" + token + "\"}");

            JsonNode authOk = awaitType(received, "auth_ok", Duration.ofSeconds(5));
            assertThat(authOk.path("userId").asText("")).isEqualTo(userId.toString());

            Map<String, String> requestHeaders = IM_CORE_REQUEST_HEADERS.poll(5, TimeUnit.SECONDS);
            assertThat(requestHeaders).isNotNull();
            assertThat(requestHeaders).containsEntry("X-Trace-Id", expectedTraceId);
            assertThat(requestHeaders.get("traceparent"))
                    .startsWith("00-" + expectedTraceId + "-")
                    .endsWith("-01");
        } finally {
            done.tryEmitEmpty();
            outbound.tryEmitComplete();
        }

        try {
            ILoggingEvent disconnectEvent = awaitLogEvent(logs, "community.action=ws_disconnect", Duration.ofSeconds(5));
            assertThat(disconnectEvent.getMDCPropertyMap()).containsEntry("traceId", expectedTraceId);
        } finally {
            stopWsLogCapture(logs);
        }
    }

    @Test
    void websocket_shouldReturnSendErrorWhenKafkaSendDoesNotCompleteWithinTimeout() throws Exception {
        when(governanceClient.validateSendPrivateMessage(anyString(), any(), anyString()))
                .thenReturn(Mono.just(CommunityGovernanceClient.Decision.allow("")));
        doReturn(new CompletableFuture<>())
                .when(commandProducer).sendPrivateText(any(SendPrivateTextCommandV1.class));

        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Empty<Void> done = Sinks.empty();
        CountDownLatch connected = new CountDownLatch(1);

        Disposable ws = openWebSocket(received, outbound, done, connected);
        try {
            assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();

            UUID userId = uuid(100);
            UUID toUserId = uuid(200);
            String token = signHs256(jwtSecret, jwtIssuer, userId.toString(), Instant.now().plusSeconds(120));
            outbound.tryEmitNext("{\"type\":\"auth\",\"accessToken\":\"" + token + "\"}");
            JsonNode authOk = awaitType(received, "auth_ok", Duration.ofSeconds(5));
            assertThat(authOk.path("userId").asText("")).isEqualTo(userId.toString());

            outbound.tryEmitNext("{\"type\":\"sendPrivateText\",\"clientMsgId\":\"c-timeout\",\"toUserId\":\"" + toUserId + "\",\"content\":\"slow-broker\"}");

            JsonNode sendError = awaitType(received, "sendError", Duration.ofSeconds(2));
            assertThat(sendError.path("cmd").asText("")).isEqualTo("sendPrivateText");
            assertThat(sendError.path("clientMsgId").asText("")).isEqualTo("c-timeout");
            assertThat(sendError.path("message").asText("")).contains("timeout");
        } finally {
            done.tryEmitEmpty();
            outbound.tryEmitComplete();
        }
    }

    private Disposable openWebSocket(
            LinkedBlockingQueue<String> received,
            Sinks.Many<String> outbound,
            Sinks.Empty<Void> done,
            CountDownLatch connected
    ) {
        return openWebSocket(received, outbound, done, connected, new HttpHeaders());
    }

    private Disposable openWebSocket(
            LinkedBlockingQueue<String> received,
            Sinks.Many<String> outbound,
            Sinks.Empty<Void> done,
            CountDownLatch connected,
            HttpHeaders headers
    ) {
        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
        URI uri = URI.create("ws://localhost:" + port + "/ws/im");
        return client.execute(uri, headers, session -> {
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
        DefaultKafkaConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(props);
        return cf.createConsumer();
    }

    private JsonNode awaitType(LinkedBlockingQueue<String> received, String type, Duration timeout) throws Exception {
        long deadlineMs = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadlineMs) {
            long waitMs = Math.max(1L, deadlineMs - System.currentTimeMillis());
            String msg = received.poll(waitMs, TimeUnit.MILLISECONDS);
            if (msg == null) {
                continue;
            }
            JsonNode node = objectMapper.readTree(msg);
            if (type.equals(node.path("type").asText(""))) {
                return node;
            }
        }
        throw new AssertionError("Timed out waiting for ws message type=" + type);
    }

    private void awaitRoomJoinedInProcess(UUID userId, UUID roomId, Duration timeout) throws Exception {
        long deadlineMs = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadlineMs) {
            List<WsConnection> conns = new ArrayList<>(connectionRegistry.listByUserId(userId));
            if (!conns.isEmpty()) {
                WsConnection conn = conns.get(0);
                if (conn.joinedRoomsView().contains(roomId) && roomIndexContains(roomId, conn.connectionId())) {
                    return;
                }
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("Timed out waiting for in-process room join (userId=" + userId + ", roomId=" + roomId + ")");
    }

    private void awaitRealtimeEventAssignments(Set<String> expectedTopics, Duration timeout) throws Exception {
        long deadlineMs = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadlineMs) {
            Set<String> assignedTopics = new HashSet<>();
            for (MessageListenerContainer container : kafkaListenerEndpointRegistry.getListenerContainers()) {
                if (container == null || !container.isRunning()) {
                    continue;
                }
                Collection<TopicPartition> partitions = container.getAssignedPartitions();
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

    private void awaitLogContains(ListAppender<ILoggingEvent> logs, String token, Duration timeout) throws Exception {
        awaitLogEvent(logs, token, timeout);
    }

    private ILoggingEvent awaitLogEvent(ListAppender<ILoggingEvent> logs, String token, Duration timeout) throws Exception {
        long deadlineMs = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadlineMs) {
            if (logs != null && logs.list != null) {
                for (ILoggingEvent event : logs.list) {
                    if (event != null && event.getFormattedMessage() != null && event.getFormattedMessage().contains(token)) {
                        return event;
                    }
                }
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("Timed out waiting for log token=" + token + " in output: " + logOutput(logs));
    }

    private ListAppender<ILoggingEvent> startWsLogCapture() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ImWebSocketHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void stopWsLogCapture(ListAppender<ILoggingEvent> appender) {
        if (appender == null) {
            return;
        }
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ImWebSocketHandler.class);
        logger.detachAppender(appender);
        appender.stop();
    }

    private String logOutput(ListAppender<ILoggingEvent> appender) {
        if (appender == null || appender.list == null || appender.list.isEmpty()) {
            return "";
        }
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    private static void ensureImCoreServer() {
        if (imCoreServer != null) {
            return;
        }
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/im/realtime/users", ImRealtimeWebSocketIntegrationTest::handleImCoreBootstrapRequest);
            server.start();
            imCoreServer = server;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start im-core mock server", e);
        }
    }

    private static void handleImCoreBootstrapRequest(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (exchange == null) {
                return;
            }
            String path = exchange.getRequestURI() == null ? "" : String.valueOf(exchange.getRequestURI().getPath());
            if (!isBootstrapRoomsPath(path)) {
                writeJson(exchange, 404, "{\"message\":\"not found\"}");
                return;
            }
            IM_CORE_REQUEST_HEADERS.offer(captureHeaders(exchange));
            if (IM_CORE_BOOTSTRAP_MODE.get() == ImCoreBootstrapMode.FAILURE) {
                writeJson(exchange, 503, "{\"message\":\"bootstrap unavailable\"}");
                return;
            }
            writeJson(exchange, 200, "{\"roomIds\":[],\"nextCursorExclusive\":null,\"hasMore\":false}");
        }
    }

    private static boolean isBootstrapRoomsPath(String path) {
        String prefix = "/internal/im/realtime/users/";
        String suffix = "/rooms";
        if (path == null || !path.startsWith(prefix) || !path.endsWith(suffix)) {
            return false;
        }
        String userId = path.substring(prefix.length(), path.length() - suffix.length());
        try {
            UUID.fromString(userId);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static Map<String, String> captureHeaders(HttpExchange exchange) {
        Map<String, String> captured = new HashMap<>();
        if (exchange == null || exchange.getRequestHeaders() == null) {
            return captured;
        }
        captured.put("X-Trace-Id", firstHeader(exchange, "X-Trace-Id"));
        captured.put("traceparent", firstHeader(exchange, "traceparent"));
        return captured;
    }

    private static String firstHeader(HttpExchange exchange, String name) {
        if (exchange == null || exchange.getRequestHeaders() == null || name == null) {
            return "";
        }
        List<String> values = exchange.getRequestHeaders().get(name);
        if (values == null || values.isEmpty() || values.get(0) == null) {
            return "";
        }
        return values.get(0);
    }

    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private boolean roomIndexContains(UUID roomId, String connectionId) {
        if (connectionId == null) {
            return false;
        }
        final boolean[] found = {false};
        roomLocalIndex.forEachConnectionId(roomId, id -> {
            if (connectionId.equals(id)) {
                found[0] = true;
            }
        });
        return found[0];
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
                for (ConsumerRecord<String, String> r : iterable) {
                    return r;
                }
            }
        }
        throw new AssertionError("Timed out waiting for record on topic " + topic);
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

    private static String conversationId(UUID userId1, UUID userId2) {
        UUID first = userId1.compareTo(userId2) <= 0 ? userId1 : userId2;
        UUID second = first.equals(userId1) ? userId2 : userId1;
        return first + "_" + second;
    }
}
