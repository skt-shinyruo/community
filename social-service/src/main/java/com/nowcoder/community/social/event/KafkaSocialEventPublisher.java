package com.nowcoder.community.social.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.EventEnvelope;
import com.nowcoder.community.common.event.EventTopics;
import com.nowcoder.community.common.event.EventTypes;
import com.nowcoder.community.common.event.payload.FollowPayload;
import com.nowcoder.community.common.event.payload.LikePayload;
import com.nowcoder.community.common.tx.AfterCommitExecutor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@ConditionalOnProperty(name = "social.events.publisher", havingValue = "kafka", matchIfMissing = true)
public class KafkaSocialEventPublisher implements SocialEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaSocialEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public KafkaSocialEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void publishLikeCreated(LikePayload payload) {
        publish(EventTypes.LIKE_CREATED, "like:" + payload.getEntityType() + ":" + payload.getEntityId(), payload);
    }

    @Override
    public void publishFollowCreated(FollowPayload payload) {
        publish(EventTypes.FOLLOW_CREATED, "follow:" + payload.getEntityType() + ":" + payload.getEntityId(), payload);
    }

    private void publish(String type, String key, Object payload) {
        try {
            EventEnvelope<Object> envelope = EventEnvelope.of(type, 1, "social-service", payload);
            String json = objectMapper.writeValueAsString(envelope);

            // 与 content-service 统一约定：若处于事务中，则在 commit 后发送，避免幽灵事件。
            AfterCommitExecutor.runAfterCommit(() -> sendAsync(EventTopics.SOCIAL_EVENTS_V1, key, json, type));
        } catch (Exception e) {
            throw new IllegalStateException("发布事件失败: " + type, e);
        }
    }

    private void sendAsync(String topic, String key, String json, String type) {
        try {
            CompletableFuture<?> future = kafkaTemplate.send(topic, key, json);
            future.whenComplete((ignored, ex) -> {
                if (ex != null) {
                    meterRegistry.counter(
                            "social_event_publish_total",
                            Tags.of("topic", topic, "type", type, "outcome", "error")
                    ).increment();
                    log.warn("[event] publish failed (topic={}, type={}, key={}): {}", topic, type, key, ex.toString());
                    return;
                }
                meterRegistry.counter(
                        "social_event_publish_total",
                        Tags.of("topic", topic, "type", type, "outcome", "ok")
                ).increment();
            });
        } catch (Exception e) {
            meterRegistry.counter(
                    "social_event_publish_total",
                    Tags.of("topic", topic, "type", type, "outcome", "error")
            ).increment();
            log.warn("[event] publish failed before send (topic={}, type={}, key={}): {}", topic, type, key, e.toString());
        }
    }
}
