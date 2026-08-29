package com.nowcoder.community.im.core.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.im.common.command.SendRoomTextCommand;
import com.nowcoder.community.im.core.application.RoomApplicationService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.mapping.AbstractJavaTypeMapper;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.nowcoder.community.im.core.support.ImCoreTestDatabaseCleaner.cleanAll;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "im.command.room-text",
                "im.command.room-text" + ".dlq",
                "im.event.room-persisted",
                "im.event.room-committed",
                "im.event.room-rejected"
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
    private RoomApplicationService roomApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        cleanAll(jdbcTemplate);
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
    void invalidRoomCommand_shouldGoToDlq_andNotBlockFollowingCommands() throws Exception {
        jdbcTemplate.update("delete from outbox_event");
        UUID sender = uuid(1);
        UUID roomId = roomApplicationService.createRoom(sender, "room").roomId();
        String badRequestId = "req-bad-" + UUID.randomUUID();
        String badClientMsgId = "c-bad-" + UUID.randomUUID();
        String okRequestId = "req-ok-" + UUID.randomUUID();
        String okClientMsgId = "c-ok-" + UUID.randomUUID();

        String dlqTopic = "im.command.room-text" + ".dlq";

        consumer = newStringConsumer("im-core-it-isolation");
        consumer.subscribe(List.of(dlqTopic, "im.event.room-persisted", "im.event.room-committed", "im.event.room-rejected"));
        consumer.poll(Duration.ofMillis(200));

        kafkaTemplate.send(
                "im.command.room-text",
                String.valueOf(roomId),
                // invalid content => should be recovered to dlq (not block partition)
                new SendRoomTextCommand(badRequestId, badClientMsgId, sender, roomId, " ", System.currentTimeMillis())
        );

        Map<String, ConsumerRecord<String, String>> rejectedBatch = pollForTopics(
                consumer,
                Set.of(dlqTopic, "im.event.room-rejected"),
                Duration.ofSeconds(10)
        );

        ConsumerRecord<String, String> dlqRecord = rejectedBatch.get(dlqTopic);
        JsonNode dlqJson = objectMapper.readTree(dlqRecord.value());
        assertThat(dlqJson.path("clientMsgId").asText("")).isEqualTo(badClientMsgId);
        assertThat(dlqJson.path("roomId").asText("")).isEqualTo(roomId.toString());

        ConsumerRecord<String, String> rejectedRecord = rejectedBatch.get("im.event.room-rejected");
        JsonNode rejectedJson = objectMapper.readTree(rejectedRecord.value());
        assertThat(rejectedJson.path("requestId").asText("")).isEqualTo(badRequestId);
        assertThat(rejectedJson.path("clientMsgId").asText("")).isEqualTo(badClientMsgId);
        assertThat(rejectedJson.path("roomId").asText("")).isEqualTo(roomId.toString());
        assertThat(rejectedJson.path("code").asInt()).isEqualTo(400);
        assertThat(rejectedJson.path("reasonCode").asText("")).isEqualTo("invalid_command");

        kafkaTemplate.send(
                "im.command.room-text",
                String.valueOf(roomId),
                new SendRoomTextCommand(okRequestId, okClientMsgId, sender, roomId, "hi", System.currentTimeMillis())
        );

        Map<String, ConsumerRecord<String, String>> persistedBatch = pollForTopics(
                consumer,
                Set.of("im.event.room-persisted", "im.event.room-committed"),
                Duration.ofSeconds(10)
        );
        JsonNode eventJson = objectMapper.readTree(persistedBatch.get("im.event.room-persisted").value());
        assertThat(eventJson.path("roomId").asText("")).isEqualTo(roomId.toString());
        assertThat(eventJson.path("seq").asLong()).isEqualTo(1L);
        assertThat(eventJson.path("fromUserId").asText("")).isEqualTo(sender.toString());
        assertThat(eventJson.has("requestId")).isFalse();
        assertThat(eventJson.has("clientMsgId")).isFalse();

        JsonNode committedJson = objectMapper.readTree(persistedBatch.get("im.event.room-committed").value());
        assertThat(committedJson.path("roomId").asText("")).isEqualTo(roomId.toString());
        assertThat(committedJson.path("requestId").asText("")).isEqualTo(okRequestId);
        assertThat(committedJson.path("clientMsgId").asText("")).isEqualTo(okClientMsgId);
    }

    @Test
    void futureSchemaVersionRoomCommand_shouldGoToDlq_andNotWriteProjectionState() throws Exception {
        jdbcTemplate.update("delete from outbox_event");
        UUID sender = uuid(2);
        UUID roomId = roomApplicationService.createRoom(sender, "room-future-schema").roomId();
        String badClientMsgId = "c-future-schema-" + UUID.randomUUID();
        String dlqTopic = "im.command.room-text" + ".dlq";

        consumer = newStringConsumer("im-core-it-future-schema");
        consumer.subscribe(List.of(dlqTopic, "im.event.room-persisted", "im.event.room-committed", "im.event.room-rejected"));
        consumer.poll(Duration.ofMillis(200));

        KafkaTemplate<String, String> stringTemplate = newStringKafkaTemplate();
        ProducerRecord<String, String> record = new ProducerRecord<>(
                "im.command.room-text",
                String.valueOf(roomId),
                """
                        {
                          "schemaVersion": 2,
                          "requestId": "req-future-schema",
                          "clientMsgId": "%s",
                          "fromUserId": "%s",
                          "roomId": "%s",
                          "content": "hi from the future",
                          "clientSentAtEpochMs": 1700000000001
                        }
                        """.formatted(badClientMsgId, sender, roomId)
        );
        record.headers().add(
                AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME,
                SendRoomTextCommand.class.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        stringTemplate.send(record).get(5, TimeUnit.SECONDS);

        ConsumerRecord<String, String> dlqRecord = pollForSingleRecord(consumer, dlqTopic, Duration.ofSeconds(10));
        JsonNode dlqJson = dlqPayloadJson(dlqRecord.value());
        assertThat(dlqJson.path("schemaVersion").asInt()).isEqualTo(2);
        assertThat(dlqJson.path("clientMsgId").asText("")).isEqualTo(badClientMsgId);

        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
        assertThat(records.records("im.event.room-persisted")).isEmpty();
        assertThat(records.records("im.event.room-committed")).isEmpty();
        assertThat(records.records("im.event.room-rejected")).isEmpty();
    }

    private Consumer<String, String> newStringConsumer(String groupId) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(groupId, "true", embeddedKafka);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        DefaultKafkaConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(props);
        return cf.createConsumer();
    }

    private KafkaTemplate<String, String> newStringKafkaTemplate() {
        Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafka);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    private JsonNode dlqPayloadJson(String value) throws Exception {
        JsonNode node = objectMapper.readTree(value);
        if (node.isTextual()) {
            byte[] raw = Base64.getDecoder().decode(node.asText());
            return objectMapper.readTree(raw);
        }
        return node;
    }

    private static ConsumerRecord<String, String> pollForSingleRecord(
            Consumer<String, String> consumer,
            String topic,
            Duration timeout
    ) {
        return pollForTopics(consumer, Set.of(topic), timeout).get(topic);
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
