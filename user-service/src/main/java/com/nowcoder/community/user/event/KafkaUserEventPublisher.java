package com.nowcoder.community.user.event;

// user-service Kafka 事件发布器：支持直发与 Outbox 两种模式。
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.EventEnvelope;
import com.nowcoder.community.common.event.EventTopics;
import com.nowcoder.community.common.event.EventTypes;
import com.nowcoder.community.common.event.payload.ModerationStatusPayload;
import com.nowcoder.community.common.tx.AfterCommitExecutor;
import com.nowcoder.community.user.outbox.OutboxEventService;
import com.nowcoder.community.user.outbox.UserOutboxProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@ConditionalOnProperty(name = "user.events.publisher", havingValue = "kafka", matchIfMissing = true)
public class KafkaUserEventPublisher implements UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaUserEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final UserOutboxProperties outboxProperties;
    private final OutboxEventService outboxEventService;

    public KafkaUserEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            UserOutboxProperties outboxProperties,
            OutboxEventService outboxEventService
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.outboxProperties = outboxProperties;
        this.outboxEventService = outboxEventService;
    }

    @Override
    public void publishModerationStatusChanged(ModerationStatusPayload payload) {
        int userId = payload == null || payload.getUserId() == null ? 0 : payload.getUserId();
        String key = "moderation-status:user:" + userId;
        publish(EventTopics.MODERATION_EVENTS_V1, EventTypes.MODERATION_STATUS_CHANGED, key, payload);
    }

    private void publish(String topic, String type, String key, Object payload) {
        try {
            EventEnvelope<Object> envelope = EventEnvelope.of(type, 1, "user-service", payload);
            String json = objectMapper.writeValueAsString(envelope);

            if (outboxProperties.isEnabled()) {
                outboxEventService.enqueue(envelope.getEventId(), topic, key, json);
                if (meterRegistry != null) {
                    meterRegistry.counter(
                            "user_event_outbox_total",
                            Tags.of("topic", topic, "type", type, "outcome", "queued")
                    ).increment();
                }
                return;
            }

            // 事务提交后再发送，避免 DB 回滚但事件已发出（幽灵事件）。
            AfterCommitExecutor.runAfterCommit(() -> sendAsync(topic, key, json, type));
        } catch (Exception e) {
            throw new IllegalStateException("发布事件失败: " + type, e);
        }
    }

    private void sendAsync(String topic, String key, String json, String type) {
        try {
            CompletableFuture<?> future = kafkaTemplate.send(topic, key, json);
            future.whenComplete((ignored, ex) -> {
                if (meterRegistry == null) {
                    return;
                }
                if (ex != null) {
                    meterRegistry.counter(
                            "user_event_publish_total",
                            Tags.of("topic", topic, "type", type, "outcome", "error")
                    ).increment();
                    log.warn("[event] publish failed (topic={}, type={}, key={}): {}", topic, type, key, ex.toString());
                    return;
                }
                meterRegistry.counter(
                        "user_event_publish_total",
                        Tags.of("topic", topic, "type", type, "outcome", "ok")
                ).increment();
            });
        } catch (Exception e) {
            if (meterRegistry != null) {
                meterRegistry.counter(
                        "user_event_publish_total",
                        Tags.of("topic", topic, "type", type, "outcome", "error")
                ).increment();
            }
            log.warn("[event] publish failed before send (topic={}, type={}, key={}): {}", topic, type, key, e.toString());
        }
    }
}

