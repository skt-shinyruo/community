package com.nowcoder.community.im.core.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.common.command.SendRoomTextCommand;
import com.nowcoder.community.im.common.policy.PrivateMessagePolicyDecision;
import com.nowcoder.community.im.core.application.RoomApplicationService;
import com.nowcoder.community.im.core.domain.model.PrivateMessageRecord;
import com.nowcoder.community.im.core.domain.model.RoomMessageRecord;
import com.nowcoder.community.im.core.domain.repository.PrivateMessageRepository;
import com.nowcoder.community.im.core.domain.repository.RoomMessageRepository;
import com.nowcoder.community.im.core.policy.PrivateMessagePolicyVerifier;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.im.core.support.ImCoreTestDatabaseCleaner.cleanAll;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "im.command.private-text",
                "im.command.room-text",
                "im.event.private-persisted",
                "im.event.room-persisted",
                "im.event.private-committed",
                "im.event.room-committed",
                "im.event.private-rejected",
                "im.event.room-rejected",
                "im.event.room-member-changed"
        }
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "events.outbox.enabled=true",
        "events.outbox.worker-fixed-delay-ms=100",
        "im.room-member-change.publisher=kafka"
})
class ImCoreKafkaIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoomApplicationService roomApplicationService;

    @Autowired
    private RoomMessageRepository roomMessageRepository;

    @Autowired
    private PrivateMessageRepository privateMessageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PrivateMessagePolicyVerifier privateMessagePolicyVerifier;

    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        cleanAll(jdbcTemplate);
        when(privateMessagePolicyVerifier.verify(any(UUID.class), any(UUID.class)))
                .thenReturn(PrivateMessagePolicyDecision.allow());
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            try {
                consumer.close();
            } catch (RuntimeException ignore) {
            }
        }
        cleanAll(jdbcTemplate);
    }

    @Test
    void roomCommand_shouldPersist_andEmitRoomPersistedEvent_withoutContent() throws Exception {
        UUID sender = uuid(1);
        UUID roomId = roomApplicationService.createRoom(sender, "room").roomId();

        // subscribe before sending to avoid missing the event
        consumer = newStringConsumer("im-core-it-room");
        consumer.subscribe(List.of("im.event.room-persisted", "im.event.room-committed"));
        // trigger partition assignment
        consumer.poll(Duration.ofMillis(200));

        kafkaTemplate.send(
                "im.command.room-text",
                String.valueOf(roomId),
                new SendRoomTextCommand("req-1", "c1", sender, roomId, "hi", System.currentTimeMillis())
        );

        Map<String, ConsumerRecord<String, String>> records = pollForTopics(
                consumer,
                List.of("im.event.room-persisted", "im.event.room-committed"),
                Duration.ofSeconds(10)
        );
        JsonNode eventJson = objectMapper.readTree(records.get("im.event.room-persisted").value());

        assertThat(eventJson.path("roomId").asText("")).isEqualTo(roomId.toString());
        assertThat(eventJson.path("seq").asLong()).isEqualTo(1L);
        assertThat(eventJson.path("fromUserId").asText("")).isEqualTo(sender.toString());
        assertThat(eventJson.has("requestId")).isFalse();
        assertThat(eventJson.has("clientMsgId")).isFalse();
        assertThat(eventJson.hasNonNull("content")).isFalse();

        JsonNode committedJson = objectMapper.readTree(records.get("im.event.room-committed").value());
        assertThat(committedJson.path("requestId").asText("")).isEqualTo("req-1");
        assertThat(committedJson.path("clientMsgId").asText("")).isEqualTo("c1");
        assertThat(committedJson.path("roomId").asText("")).isEqualTo(roomId.toString());
        assertThat(committedJson.path("seq").asLong()).isEqualTo(1L);

        List<RoomMessageRecord> rows = roomMessageRepository.listAfterSeq(roomId, 0, 10);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).content()).isEqualTo("hi");
    }

    @Test
    void privateCommand_shouldPersist_andEmitPrivatePersistedEvent_withContent() throws Exception {
        UUID fromUserId = uuid(101);
        UUID toUserId = uuid(202);
        String conversationId = fromUserId + "_" + toUserId;

        consumer = newStringConsumer("im-core-it-private");
        consumer.subscribe(List.of("im.event.private-persisted", "im.event.private-committed"));
        consumer.poll(Duration.ofMillis(200));

        kafkaTemplate.send(
                "im.command.private-text",
                conversationId,
                new SendPrivateTextCommand("req-1", "c1", fromUserId, toUserId, conversationId, "hello", System.currentTimeMillis())
        );

        Map<String, ConsumerRecord<String, String>> records = pollForTopics(
                consumer,
                List.of("im.event.private-persisted", "im.event.private-committed"),
                Duration.ofSeconds(10)
        );
        JsonNode eventJson = objectMapper.readTree(records.get("im.event.private-persisted").value());

        assertThat(eventJson.path("conversationId").asText("")).isEqualTo(conversationId);
        assertThat(eventJson.path("seq").asLong()).isEqualTo(1L);
        assertThat(eventJson.path("fromUserId").asText("")).isEqualTo(fromUserId.toString());
        assertThat(eventJson.path("toUserId").asText("")).isEqualTo(toUserId.toString());
        assertThat(eventJson.path("content").asText("")).isEqualTo("hello");
        assertThat(eventJson.has("requestId")).isFalse();
        assertThat(eventJson.has("clientMsgId")).isFalse();

        JsonNode committedJson = objectMapper.readTree(records.get("im.event.private-committed").value());
        assertThat(committedJson.path("requestId").asText("")).isEqualTo("req-1");
        assertThat(committedJson.path("clientMsgId").asText("")).isEqualTo("c1");
        assertThat(committedJson.path("conversationId").asText("")).isEqualTo(conversationId);
        assertThat(committedJson.path("seq").asLong()).isEqualTo(1L);

        List<PrivateMessageRecord> rows = privateMessageRepository.listAfterSeq(conversationId, 0, 10);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).content()).isEqualTo("hello");
    }

    @Test
    void roomMembershipJoin_shouldEmitRoomMemberChangedEvent() throws Exception {
        UUID owner = uuid(1);
        UUID member = uuid(3);
        UUID roomId = roomApplicationService.createRoom(owner, "room").roomId();

        consumer = newStringConsumer("im-core-it-room-member-changed");
        consumer.subscribe(List.of("im.event.room-member-changed"));
        awaitAssignment(consumer, Duration.ofSeconds(5));

        long beforeJoin = System.currentTimeMillis();
        roomApplicationService.joinRoom(member, roomId);

        // createRoom queues the owner's JOINED event via the after-commit publisher; it can
        // land before or after any seek, so wait specifically for this member's JOINED record.
        ConsumerRecord<String, String> changedRecord =
                pollForJoinedRecord(consumer, member, Duration.ofSeconds(30));
        JsonNode eventJson = objectMapper.readTree(changedRecord.value());

        assertThat(changedRecord.key()).isEqualTo(roomId.toString());
        assertThat(eventJson.path("roomId").asText("")).isEqualTo(roomId.toString());
        assertThat(eventJson.path("userId").asText("")).isEqualTo(member.toString());
        assertThat(eventJson.path("action").asText("")).isEqualTo("JOINED");
        assertThat(eventJson.path("occurredAtEpochMillis").asLong()).isGreaterThanOrEqualTo(beforeJoin);
    }

    private Consumer<String, String> newStringConsumer(String groupId) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(groupId, "true", embeddedKafka);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        DefaultKafkaConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(props);
        return cf.createConsumer();
    }

    private static void awaitAssignment(Consumer<String, String> consumer, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            consumer.poll(Duration.ofMillis(200));
            if (!consumer.assignment().isEmpty()) {
                return;
            }
        }
        throw new AssertionError("Timed out waiting for consumer assignment");
    }

    private ConsumerRecord<String, String> pollForJoinedRecord(
            Consumer<String, String> consumer,
            UUID member,
            Duration timeout
    ) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
            if (records == null || records.isEmpty()) {
                continue;
            }
            for (ConsumerRecord<String, String> r : records.records("im.event.room-member-changed")) {
                try {
                    JsonNode json = objectMapper.readTree(r.value());
                    if (member.toString().equals(json.path("userId").asText(""))
                            && "JOINED".equals(json.path("action").asText(""))) {
                        return r;
                    }
                } catch (Exception ignore) {
                    // 继续等待目标记录；非 JSON 内容与本测试无关。
                }
            }
        }
        throw new AssertionError("Timed out waiting for JOINED record for member " + member);
    }

    private static Map<String, ConsumerRecord<String, String>> pollForTopics(
            Consumer<String, String> consumer,
            List<String> topics,
            Duration timeout
    ) {
        java.util.LinkedHashMap<String, ConsumerRecord<String, String>> found = new java.util.LinkedHashMap<>();
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
            if (records == null || records.isEmpty()) {
                continue;
            }
            for (String topic : topics) {
                if (found.containsKey(topic)) {
                    continue;
                }
                Iterable<ConsumerRecord<String, String>> iterable = records.records(topic);
                if (iterable == null) {
                    continue;
                }
                for (ConsumerRecord<String, String> r : iterable) {
                    found.put(topic, r);
                    break;
                }
            }
            if (found.keySet().containsAll(topics)) {
                return found;
            }
        }
        throw new AssertionError("Timed out waiting for records on topics " + topics + ", found=" + found.keySet());
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
