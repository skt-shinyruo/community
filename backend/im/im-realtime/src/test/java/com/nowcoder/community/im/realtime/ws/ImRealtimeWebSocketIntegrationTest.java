package com.nowcoder.community.im.realtime.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nowcoder.community.im.contracts.ImTopics;
import com.nowcoder.community.im.contracts.event.PrivateMessagePersistedEventV1;
import com.nowcoder.community.im.contracts.event.RoomMemberChangedEventV1;
import com.nowcoder.community.im.contracts.event.RoomMessagePersistedEventV1;
import com.nowcoder.community.im.realtime.client.CommunityGovernanceClient;
import com.nowcoder.community.im.realtime.presence.ConnectionRegistry;
import com.nowcoder.community.im.realtime.presence.RoomLocalIndex;
import com.nowcoder.community.im.realtime.presence.WsConnection;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.WebSocketMessage;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
                ImTopics.EVENT_ROOM_MEMBER_CHANGED_V1
        }
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "im.ws.room-flush-interval-ms=10"
})
class ImRealtimeWebSocketIntegrationTest {

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

    @MockBean
    private CommunityGovernanceClient governanceClient;

    @Value("${security.jwt.hmac-secret}")
    private String jwtSecret;

    private Consumer<String, String> commandConsumer;

    @AfterEach
    void tearDown() {
        if (commandConsumer != null) {
            try {
                commandConsumer.close();
            } catch (RuntimeException ignore) {
            }
        }
    }

    @Test
    void websocket_shouldAuth_sendCommands_andReceivePrivateAndRoomPush() throws Exception {
        when(governanceClient.validateSendPrivateMessage(anyString(), anyInt()))
                .thenReturn(Mono.just(CommunityGovernanceClient.Decision.allow("")));

        int userId = 100;
        int toUserId = 200;
        long roomId = 10L;

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

            String token = signHs256(jwtSecret, String.valueOf(userId), Instant.now().plusSeconds(120));
            outbound.tryEmitNext("{\"type\":\"auth\",\"accessToken\":\"" + token + "\"}");

            JsonNode authOk = awaitType(received, "auth_ok", Duration.ofSeconds(5));
            assertThat(authOk.path("userId").asInt()).isEqualTo(userId);

            outbound.tryEmitNext("{\"type\":\"sendPrivateText\",\"clientMsgId\":\"c1\",\"toUserId\":" + toUserId + ",\"content\":\"hi\"}");
            JsonNode privateAck = awaitType(received, "sendAck", Duration.ofSeconds(5));
            assertThat(privateAck.path("cmd").asText("")).isEqualTo("sendPrivateText");
            assertThat(privateAck.path("clientMsgId").asText("")).isEqualTo("c1");
            ConsumerRecord<String, String> privateCmdRecord = pollForSingleRecord(commandConsumer, ImTopics.COMMAND_PRIVATE_TEXT_V1, Duration.ofSeconds(10));
            JsonNode privateCmd = objectMapper.readTree(privateCmdRecord.value());
            assertThat(privateCmd.path("fromUserId").asInt()).isEqualTo(userId);
            assertThat(privateCmd.path("toUserId").asInt()).isEqualTo(toUserId);
            assertThat(privateCmd.path("conversationId").asText("")).isEqualTo("100_200");
            assertThat(privateCmd.path("content").asText("")).isEqualTo("hi");
            assertThat(privateCmd.path("clientMsgId").asText("")).isEqualTo("c1");

            outbound.tryEmitNext("{\"type\":\"sendRoomText\",\"clientMsgId\":\"c2\",\"roomId\":" + roomId + ",\"content\":\"hello\"}");
            JsonNode roomAck = awaitType(received, "sendAck", Duration.ofSeconds(5));
            assertThat(roomAck.path("cmd").asText("")).isEqualTo("sendRoomText");
            assertThat(roomAck.path("clientMsgId").asText("")).isEqualTo("c2");
            ConsumerRecord<String, String> roomCmdRecord = pollForSingleRecord(commandConsumer, ImTopics.COMMAND_ROOM_TEXT_V1, Duration.ofSeconds(10));
            JsonNode roomCmd = objectMapper.readTree(roomCmdRecord.value());
            assertThat(roomCmd.path("fromUserId").asInt()).isEqualTo(userId);
            assertThat(roomCmd.path("roomId").asLong()).isEqualTo(roomId);
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
                    new RoomMessagePersistedEventV1("evt-room", roomId, 7L, 9001L, userId, System.currentTimeMillis())
            ).get(5, TimeUnit.SECONDS);

            JsonNode roomUpdated = awaitType(received, "roomUpdatedBatch", Duration.ofSeconds(5));
            assertThat(roomUpdated.hasNonNull("content")).isFalse();
            assertThat(roomUpdated.path("items").isArray()).isTrue();
            boolean match = false;
            for (JsonNode item : roomUpdated.path("items")) {
                if (item.path("roomId").asLong() == roomId && item.path("lastSeq").asLong() == 7L) {
                    match = true;
                    break;
                }
            }
            assertThat(match).isTrue();

            kafkaTemplate.send(
                    ImTopics.EVENT_PRIVATE_PERSISTED_V1,
                    "100_200",
                    new PrivateMessagePersistedEventV1(
                            "evt-private",
                            "100_200",
                            3L,
                            12345L,
                            userId,
                            toUserId,
                            "server-hi",
                            System.currentTimeMillis()
                    )
            );

            JsonNode privateMsg = awaitType(received, "privateMessage", Duration.ofSeconds(5));
            assertThat(privateMsg.path("conversationId").asText("")).isEqualTo("100_200");
            assertThat(privateMsg.path("fromUserId").asInt()).isEqualTo(userId);
            assertThat(privateMsg.path("toUserId").asInt()).isEqualTo(toUserId);
            assertThat(privateMsg.path("content").asText("")).isEqualTo("server-hi");
        } finally {
            done.tryEmitEmpty();
            outbound.tryEmitComplete();
            ws.dispose();
        }
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

    private void awaitRoomJoinedInProcess(int userId, long roomId, Duration timeout) throws Exception {
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

    private boolean roomIndexContains(long roomId, String connectionId) {
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

    private static String signHs256(String secret, String sub, Instant exp) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(sub)
                .issueTime(new Date())
                .expirationTime(Date.from(exp))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
