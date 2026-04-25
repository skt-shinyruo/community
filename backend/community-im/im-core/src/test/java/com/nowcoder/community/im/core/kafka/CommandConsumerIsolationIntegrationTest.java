package com.nowcoder.community.im.core.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.im.common.ImTopics;
import com.nowcoder.community.im.common.command.SendRoomTextCommand;
import com.nowcoder.community.im.core.service.RoomMembershipService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EmbeddedKafka(
        partitions = 1,
        topics = {
                ImTopics.COMMAND_ROOM_TEXT,
                "im.command.room_text.v1.dlq",
                ImTopics.EVENT_ROOM_PERSISTED,
                ImTopics.EVENT_ROOM_REJECTED
        }
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "events.outbox.enabled=true",
        "events.outbox.worker-fixed-delay-ms=100"
})
class CommandConsumerIsolationIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoomMembershipService roomMembershipService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Consumer<String, String> consumer;

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            try {
                consumer.close();
            } catch (RuntimeException ignore) {
            }
        }
    }

    @Test
    void invalidRoomCommand_shouldGoToDlq_andNotBlockFollowingCommands() throws Exception {
        jdbcTemplate.update("delete from outbox_event");
        UUID sender = uuid(1);
        UUID roomId = roomMembershipService.createRoom(sender, "room");
        String badRequestId = "req-bad-" + UUID.randomUUID();
        String badClientMsgId = "c-bad-" + UUID.randomUUID();
        String okRequestId = "req-ok-" + UUID.randomUUID();
        String okClientMsgId = "c-ok-" + UUID.randomUUID();

        String dlqTopic = ImTopics.COMMAND_ROOM_TEXT + ".dlq";

        consumer = newStringConsumer("im-core-it-isolation");
        consumer.subscribe(List.of(dlqTopic, ImTopics.EVENT_ROOM_PERSISTED, ImTopics.EVENT_ROOM_REJECTED));
        consumer.poll(Duration.ofMillis(200));

        kafkaTemplate.send(
                ImTopics.COMMAND_ROOM_TEXT,
                String.valueOf(roomId),
                // invalid content => should be recovered to dlq (not block partition)
                new SendRoomTextCommand(badRequestId, badClientMsgId, sender, roomId, " ", System.currentTimeMillis())
        );

        Map<String, ConsumerRecord<String, String>> rejectedBatch = pollForTopics(
                consumer,
                Set.of(dlqTopic, ImTopics.EVENT_ROOM_REJECTED),
                Duration.ofSeconds(10)
        );

        ConsumerRecord<String, String> dlqRecord = rejectedBatch.get(dlqTopic);
        JsonNode dlqJson = objectMapper.readTree(dlqRecord.value());
        assertThat(dlqJson.path("clientMsgId").asText("")).isEqualTo(badClientMsgId);
        assertThat(dlqJson.path("roomId").asText("")).isEqualTo(roomId.toString());

        ConsumerRecord<String, String> rejectedRecord = rejectedBatch.get(ImTopics.EVENT_ROOM_REJECTED);
        JsonNode rejectedJson = objectMapper.readTree(rejectedRecord.value());
        assertThat(rejectedJson.path("requestId").asText("")).isEqualTo(badRequestId);
        assertThat(rejectedJson.path("clientMsgId").asText("")).isEqualTo(badClientMsgId);
        assertThat(rejectedJson.path("roomId").asText("")).isEqualTo(roomId.toString());
        assertThat(rejectedJson.path("code").asInt()).isEqualTo(400);
        assertThat(rejectedJson.path("reasonCode").asText("")).isEqualTo("invalid_command");

        kafkaTemplate.send(
                ImTopics.COMMAND_ROOM_TEXT,
                String.valueOf(roomId),
                new SendRoomTextCommand(okRequestId, okClientMsgId, sender, roomId, "hi", System.currentTimeMillis())
        );

        ConsumerRecord<String, String> eventRecord = pollForSingleRecord(consumer, ImTopics.EVENT_ROOM_PERSISTED, Duration.ofSeconds(10));
        JsonNode eventJson = objectMapper.readTree(eventRecord.value());
        assertThat(eventJson.path("roomId").asText("")).isEqualTo(roomId.toString());
        assertThat(eventJson.path("seq").asLong()).isEqualTo(1L);
        assertThat(eventJson.path("fromUserId").asText("")).isEqualTo(sender.toString());
        assertThat(eventJson.path("requestId").asText("")).isEqualTo(okRequestId);
        assertThat(eventJson.path("clientMsgId").asText("")).isEqualTo(okClientMsgId);
    }

    private Consumer<String, String> newStringConsumer(String groupId) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(groupId, "true", embeddedKafka);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        DefaultKafkaConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(props);
        return cf.createConsumer();
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

    private static Map<String, ConsumerRecord<String, String>> pollForTopics(
            Consumer<String, String> consumer,
            Set<String> topics,
            Duration timeout
    ) {
        Instant deadline = Instant.now().plus(timeout);
        HashMap<String, ConsumerRecord<String, String>> found = new HashMap<>();
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
                for (ConsumerRecord<String, String> record : iterable) {
                    found.put(topic, record);
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
