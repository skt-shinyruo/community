package com.nowcoder.community.content.event;

// Kafka 事件发布器：支持直发与 Outbox 两种模式。
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.nowcoder.community.contracts.event.EventEnvelope;
import com.nowcoder.community.contracts.event.EventTopics;
import com.nowcoder.community.infra.trace.TraceId;
import com.nowcoder.community.infra.tx.AfterCommitExecutor;
import com.nowcoder.community.content.api.event.ContentEventTypes;
import com.nowcoder.community.content.api.event.payload.CommentPayload;
import com.nowcoder.community.content.api.event.payload.ModerationCommandPayload;
import com.nowcoder.community.content.api.event.payload.ModerationPayload;
import com.nowcoder.community.content.api.event.payload.PostPayload;
import com.nowcoder.community.infra.outbox.OutboxEventService;
import com.nowcoder.community.infra.outbox.OutboxProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@ConditionalOnProperty(name = "content.events.publisher", havingValue = "kafka", matchIfMissing = true)
public class KafkaContentEventPublisher implements ContentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaContentEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final OutboxProperties outboxProperties;
    private final OutboxEventService outboxEventService;

    public KafkaContentEventPublisher(
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
    public void publishPostPublished(PostPayload payload) {
        publish(EventTopics.POST_EVENTS_V1, ContentEventTypes.POST_PUBLISHED, "post:" + payload.getPostId(), payload);
    }

    @Override
    public void publishPostUpdated(PostPayload payload) {
        publish(EventTopics.POST_EVENTS_V1, ContentEventTypes.POST_UPDATED, "post:" + payload.getPostId(), payload);
    }

    @Override
    public void publishPostDeleted(PostPayload payload) {
        publish(EventTopics.POST_EVENTS_V1, ContentEventTypes.POST_DELETED, "post:" + payload.getPostId(), payload);
    }

    @Override
    public void publishCommentCreated(CommentPayload payload) {
        publish(EventTopics.COMMENT_EVENTS_V1, ContentEventTypes.COMMENT_CREATED, "comment:" + payload.getCommentId(), payload);
    }

    @Override
    public void publishCommentDeleted(CommentPayload payload) {
        publish(EventTopics.COMMENT_EVENTS_V1, ContentEventTypes.COMMENT_DELETED, "comment:" + payload.getCommentId(), payload);
    }

    @Override
    public void publishModerationActionApplied(ModerationPayload payload) {
        int toUserId = payload == null || payload.getToUserId() == null ? 0 : payload.getToUserId();
        String key = "moderation:" + (payload == null ? "0" : String.valueOf(payload.getReportId())) + ":to:" + toUserId;
        publish(EventTopics.MODERATION_EVENTS_V1, ContentEventTypes.MODERATION_ACTION_APPLIED, key, payload);
    }

    @Override
    public void publishModerationCommandRequested(ModerationCommandPayload payload) {
        int userId = payload == null || payload.getUserId() == null ? 0 : payload.getUserId();
        String reportId = payload == null || payload.getReportId() == null ? "0" : String.valueOf(payload.getReportId());
        String key = "moderation-cmd:user:" + userId + ":report:" + reportId;
        publish(EventTopics.MODERATION_EVENTS_V1, ContentEventTypes.MODERATION_COMMAND_REQUESTED, key, payload);
    }

    private void publish(String topic, String type, String key, Object payload) {
        EventEnvelope<Object> envelope = EventEnvelope.of(type, 1, "content-service", payload, TraceId.get());
        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("发布事件失败: " + type, e);
        }

        if (outboxProperties.isEnabled()) {
            outboxEventService.enqueue(envelope.getEventId(), topic, key, json);
            meterRegistry.counter(
                    "content_event_outbox_total",
                    Tags.of("topic", topic, "type", type, "outcome", "queued")
            ).increment();
            return;
        }

        if (!outboxProperties.isDirectSendEnabled()) {
            meterRegistry.counter(
                    "content_event_publish_total",
                    Tags.of("topic", topic, "type", type, "outcome", "blocked_direct_send")
            ).increment();
            throw new IllegalStateException("events.outbox.enabled=false 且 events.outbox.direct-send-enabled=false，禁止直发: " + type);
        }

        // 事务提交后再发送，避免 DB 回滚但事件已发出（幽灵事件）。
        AfterCommitExecutor.runAfterCommit(() -> sendAsync(topic, key, json, type));
    }

    private void sendAsync(String topic, String key, String json, String type) {
        try {
            CompletableFuture<?> future = kafkaTemplate.send(topic, key, json);
            future.whenComplete((ignored, ex) -> {
                if (ex != null) {
                    meterRegistry.counter(
                            "content_event_publish_total",
                            Tags.of("topic", topic, "type", type, "outcome", "error")
                    ).increment();
                    log.warn("[event] publish failed (topic={}, type={}, key={}): {}", topic, type, key, ex.toString());
                    return;
                }
                meterRegistry.counter(
                        "content_event_publish_total",
                        Tags.of("topic", topic, "type", type, "outcome", "ok")
                ).increment();
            });
        } catch (RuntimeException e) {
            meterRegistry.counter(
                    "content_event_publish_total",
                    Tags.of("topic", topic, "type", type, "outcome", "error")
            ).increment();
            log.warn("[event] publish failed before send (topic={}, type={}, key={}): {}", topic, type, key, e.toString());
        }
    }
}
