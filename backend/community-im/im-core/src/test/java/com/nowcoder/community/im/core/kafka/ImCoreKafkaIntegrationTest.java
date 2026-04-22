package com.nowcoder.community.im.core.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.im.common.ImTopics;
import com.nowcoder.community.im.common.command.SendPrivateTextCommandV1;
import com.nowcoder.community.im.common.command.SendRoomTextCommandV1;
import com.nowcoder.community.im.core.repository.PrivateMessageRepository;
import com.nowcoder.community.im.core.repository.RoomMessageRepository;
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
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {
                ImTopics.COMMAND_PRIVATE_TEXT_V1,
                ImTopics.COMMAND_ROOM_TEXT_V1,
                ImTopics.EVENT_PRIVATE_PERSISTED_V1,
                ImTopics.EVENT_ROOM_PERSISTED_V1
        }
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
class ImCoreKafkaIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoomMembershipService roomMembershipService;

    @Autowired
    private RoomMessageRepository roomMessageRepository;

    @Autowired
    private PrivateMessageRepository privateMessageRepository;

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
    void roomCommand_shouldPersist_andEmitRoomPersistedEvent_withoutContent() throws Exception {
        UUID sender = uuid(1);
        UUID roomId = roomMembershipService.createRoom(sender, "room");

        // subscribe before sending to avoid missing the event
        consumer = newStringConsumer("im-core-it-room");
        consumer.subscribe(List.of(ImTopics.EVENT_ROOM_PERSISTED_V1));
        // trigger partition assignment
        consumer.poll(Duration.ofMillis(200));

        kafkaTemplate.send(
                ImTopics.COMMAND_ROOM_TEXT_V1,
                String.valueOf(roomId),
                new SendRoomTextCommandV1("req-1", "c1", sender, roomId, "hi", System.currentTimeMillis())
        );

        ConsumerRecord<String, String> record = pollForSingleRecord(consumer, ImTopics.EVENT_ROOM_PERSISTED_V1, Duration.ofSeconds(10));
        JsonNode eventJson = objectMapper.readTree(record.value());

        assertThat(eventJson.path("roomId").asText("")).isEqualTo(roomId.toString());
        assertThat(eventJson.path("seq").asLong()).isEqualTo(1L);
        assertThat(eventJson.path("fromUserId").asText("")).isEqualTo(sender.toString());
        assertThat(eventJson.hasNonNull("content")).isFalse();

        List<RoomMessageRepository.RoomMessageRow> rows = roomMessageRepository.listAfterSeq(roomId, 0, 10);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).content()).isEqualTo("hi");
    }

    @Test
    void privateCommand_shouldPersist_andEmitPrivatePersistedEvent_withContent() throws Exception {
        UUID fromUserId = uuid(101);
        UUID toUserId = uuid(202);
        String conversationId = fromUserId + "_" + toUserId;

        consumer = newStringConsumer("im-core-it-private");
        consumer.subscribe(List.of(ImTopics.EVENT_PRIVATE_PERSISTED_V1));
        consumer.poll(Duration.ofMillis(200));

        kafkaTemplate.send(
                ImTopics.COMMAND_PRIVATE_TEXT_V1,
                conversationId,
                new SendPrivateTextCommandV1("req-1", "c1", fromUserId, toUserId, conversationId, "hello", System.currentTimeMillis())
        );

        ConsumerRecord<String, String> record = pollForSingleRecord(consumer, ImTopics.EVENT_PRIVATE_PERSISTED_V1, Duration.ofSeconds(10));
        JsonNode eventJson = objectMapper.readTree(record.value());

        assertThat(eventJson.path("conversationId").asText("")).isEqualTo(conversationId);
        assertThat(eventJson.path("seq").asLong()).isEqualTo(1L);
        assertThat(eventJson.path("fromUserId").asText("")).isEqualTo(fromUserId.toString());
        assertThat(eventJson.path("toUserId").asText("")).isEqualTo(toUserId.toString());
        assertThat(eventJson.path("content").asText("")).isEqualTo("hello");

        List<PrivateMessageRepository.PrivateMessageRow> rows = privateMessageRepository.listAfterSeq(conversationId, 0, 10);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).content()).isEqualTo("hello");
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

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
