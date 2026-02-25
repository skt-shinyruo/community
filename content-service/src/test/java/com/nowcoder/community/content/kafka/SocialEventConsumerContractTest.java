package com.nowcoder.community.content.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.contracts.event.EventEnvelope;
import com.nowcoder.community.content.score.PostScoreQueue;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * consumer 契约测试：unknown type / unsupported version 的处理策略必须可配置。
 *
 * <p>说明：这里验证的是 consumer 行为（skip vs fail-closed），而不是 Kafka error handler 的实现细节。</p>
 */
class SocialEventConsumerContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void unknownType_shouldSkipWhenConfiguredToSkip() throws Exception {
        SocialEventConsumer consumer = newConsumer("SKIP", "DLQ");
        ConsumerRecord<String, String> record = record("unknown.type", 1);
        consumer.handleRecord(record);
    }

    @Test
    void unknownType_shouldFailClosedWhenConfiguredToDlq() throws Exception {
        SocialEventConsumer consumer = newConsumer("DLQ", "DLQ");
        ConsumerRecord<String, String> record = record("unknown.type", 1);
        assertThatThrownBy(() -> consumer.handleRecord(record))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported event type");
    }

    @Test
    void unsupportedVersion_shouldSkipWhenConfiguredToSkip() throws Exception {
        SocialEventConsumer consumer = newConsumer("SKIP", "SKIP");
        ConsumerRecord<String, String> record = record("unknown.type", 2);
        consumer.handleRecord(record);
    }

    @Test
    void unsupportedVersion_shouldFailClosedWhenConfiguredToDlq() throws Exception {
        SocialEventConsumer consumer = newConsumer("SKIP", "DLQ");
        ConsumerRecord<String, String> record = record("unknown.type", 2);
        assertThatThrownBy(() -> consumer.handleRecord(record))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported envelope version");
    }

    @Test
    void invalidConfig_shouldFallbackToDefault() throws Exception {
        // unknownTypeAction 默认 SKIP；unsupportedVersionAction 默认 DLQ
        SocialEventConsumer consumer = newConsumer("NOT_VALID", "NOT_VALID");
        consumer.handleRecord(record("unknown.type", 1));

        assertThatThrownBy(() -> consumer.handleRecord(record("unknown.type", 2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SocialEventConsumer newConsumer(String unknownTypeAction, String unsupportedVersionAction) {
        PostScoreQueue postScoreQueue = new PostScoreQueue() {
            @Override
            public void add(int postId) {
                // no-op
            }

            @Override
            public Integer pop() {
                return null;
            }
        };
        return new SocialEventConsumer(
                objectMapper,
                postScoreQueue,
                null,
                "db",
                unknownTypeAction,
                unsupportedVersionAction
        );
    }

    private ConsumerRecord<String, String> record(String type, int version) throws Exception {
        EventEnvelope<Object> env = EventEnvelope.of(type, version, "contract-test", Map.of("k", "v"));
        env.setEventId("evt_1");
        env.setTraceId("trace_1");
        String json = objectMapper.writeValueAsString(env);
        return new ConsumerRecord<>("contract-topic", 0, 0L, null, json);
    }
}
