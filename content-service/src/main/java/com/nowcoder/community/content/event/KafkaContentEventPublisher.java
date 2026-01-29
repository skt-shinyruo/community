package com.nowcoder.community.content.event;

// Kafka 事件发布器：支持直发与 Outbox 两种模式。
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.EventEnvelope;
import com.nowcoder.community.common.event.EventTopics;
import com.nowcoder.community.common.event.EventTypes;
import com.nowcoder.community.common.event.payload.CommentPayload;
import com.nowcoder.community.common.event.payload.ModerationCommandPayload;
import com.nowcoder.community.common.event.payload.ModerationPayload;
import com.nowcoder.community.common.event.payload.PostPayload;
import com.nowcoder.community.common.tx.AfterCommitExecutor;
import com.nowcoder.community.content.outbox.ContentOutboxProperties;
import com.nowcoder.community.content.outbox.OutboxEventService;
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
    private final ContentOutboxProperties outboxProperties;
    private final OutboxEventService outboxEventService;

    public KafkaContentEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            ContentOutboxProperties outboxProperties,
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
        publish(EventTopics.POST_EVENTS_V1, EventTypes.POST_PUBLISHED, "post:" + payload.getPostId(), payload);
    }

    @Override
    public void publishPostUpdated(PostPayload payload) {
        publish(EventTopics.POST_EVENTS_V1, EventTypes.POST_UPDATED, "post:" + payload.getPostId(), payload);
    }

    @Override
    public void publishPostDeleted(PostPayload payload) {
        publish(EventTopics.POST_EVENTS_V1, EventTypes.POST_DELETED, "post:" + payload.getPostId(), payload);
    }

    @Override
    public void publishCommentCreated(CommentPayload payload) {
        publish(EventTopics.COMMENT_EVENTS_V1, EventTypes.COMMENT_CREATED, "comment:" + payload.getCommentId(), payload);
    }

    @Override
    public void publishModerationActionApplied(ModerationPayload payload) {
        int toUserId = payload == null || payload.getToUserId() == null ? 0 : payload.getToUserId();
        String key = "moderation:" + (payload == null ? "0" : String.valueOf(payload.getReportId())) + ":to:" + toUserId;
        publish(EventTopics.MODERATION_EVENTS_V1, EventTypes.MODERATION_ACTION_APPLIED, key, payload);
    }

    @Override
    public void publishModerationCommandRequested(ModerationCommandPayload payload) {
        int userId = payload == null || payload.getUserId() == null ? 0 : payload.getUserId();
        String reportId = payload == null || payload.getReportId() == null ? "0" : String.valueOf(payload.getReportId());
        String key = "moderation-cmd:user:" + userId + ":report:" + reportId;
        publish(EventTopics.MODERATION_EVENTS_V1, EventTypes.MODERATION_COMMAND_REQUESTED, key, payload);
    }

    private void publish(String topic, String type, String key, Object payload) {
        try {
            EventEnvelope<Object> envelope = EventEnvelope.of(type, 1, "content-service", payload);
            String json = objectMapper.writeValueAsString(envelope);

            if (outboxProperties.isEnabled()) {
                outboxEventService.enqueue(envelope.getEventId(), topic, key, json);
                meterRegistry.counter(
                        "content_event_outbox_total",
                        Tags.of("topic", topic, "type", type, "outcome", "queued")
                ).increment();
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
        } catch (Exception e) {
            meterRegistry.counter(
                    "content_event_publish_total",
                    Tags.of("topic", topic, "type", type, "outcome", "error")
            ).increment();
            log.warn("[event] publish failed before send (topic={}, type={}, key={}): {}", topic, type, key, e.toString());
        }
    }
}
