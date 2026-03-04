package com.nowcoder.community.social.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.contracts.event.EventEnvelope;
import com.nowcoder.community.contracts.event.EventTopics;
import com.nowcoder.community.infra.trace.TraceId;
import com.nowcoder.community.infra.tx.AfterCommitExecutor;
import com.nowcoder.community.infra.outbox.OutboxEventService;
import com.nowcoder.community.infra.outbox.OutboxProperties;
import com.nowcoder.community.social.api.event.SocialEventTypes;
import com.nowcoder.community.social.api.event.payload.BlockPayload;
import com.nowcoder.community.social.api.event.payload.FollowPayload;
import com.nowcoder.community.social.api.event.payload.LikePayload;
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
    private final OutboxProperties outboxProperties;
    private final OutboxEventService outboxEventService;

    public KafkaSocialEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            OutboxProperties outboxProperties,
            OutboxEventService outboxEventService
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.outboxProperties = outboxProperties;
        this.outboxEventService = outboxEventService;
    }

    @Override
    public void publishLikeCreated(LikePayload payload) {
        publish(SocialEventTypes.LIKE_CREATED, "like:" + payload.getEntityType() + ":" + payload.getEntityId(), payload);
    }

    @Override
    public void publishLikeRemoved(LikePayload payload) {
        publish(SocialEventTypes.LIKE_REMOVED, "like:" + payload.getEntityType() + ":" + payload.getEntityId(), payload);
    }

    @Override
    public void publishFollowCreated(FollowPayload payload) {
        publish(SocialEventTypes.FOLLOW_CREATED, "follow:" + payload.getEntityType() + ":" + payload.getEntityId(), payload);
    }

    @Override
    public void publishBlockRelationChanged(BlockPayload payload) {
        int blocker = payload == null || payload.getBlockerUserId() == null ? 0 : payload.getBlockerUserId();
        int blocked = payload == null || payload.getBlockedUserId() == null ? 0 : payload.getBlockedUserId();
        publish(SocialEventTypes.BLOCK_RELATION_CHANGED, "block:" + blocker + ":" + blocked, payload);
    }

    private void publish(String type, String key, Object payload) {
        try {
            EventEnvelope<Object> envelope = EventEnvelope.of(type, 1, "social-service", payload, TraceId.get());
            String json = objectMapper.writeValueAsString(envelope);

            if (outboxProperties.isEnabled()) {
                outboxEventService.enqueue(envelope.getEventId(), EventTopics.SOCIAL_EVENTS_V1, key, json);
                meterRegistry.counter(
                        "social_event_outbox_total",
                        Tags.of("topic", EventTopics.SOCIAL_EVENTS_V1, "type", type, "outcome", "queued")
                ).increment();
                return;
            }

            if (!outboxProperties.isDirectSendEnabled()) {
                meterRegistry.counter(
                        "social_event_publish_total",
                        Tags.of("topic", EventTopics.SOCIAL_EVENTS_V1, "type", type, "outcome", "blocked_direct_send")
                ).increment();
                throw new IllegalStateException("events.outbox.enabled=false 且 events.outbox.direct-send-enabled=false，禁止直发: " + type);
            }

            // 与 content-service 统一约定：若处于事务中，则在 commit 后发送，避免幽灵事件。
            AfterCommitExecutor.runAfterCommit(() -> sendAsync(EventTopics.SOCIAL_EVENTS_V1, key, json, type));
        } catch (JsonProcessingException e) {
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
        } catch (RuntimeException e) {
            meterRegistry.counter(
                    "social_event_publish_total",
                    Tags.of("topic", topic, "type", type, "outcome", "error")
            ).increment();
            log.warn("[event] publish failed before send (topic={}, type={}, key={}): {}", topic, type, key, e.toString());
        }
    }
}
