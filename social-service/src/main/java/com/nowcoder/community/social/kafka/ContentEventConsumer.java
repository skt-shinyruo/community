package com.nowcoder.community.social.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.contracts.domain.EntityTypes;
import com.nowcoder.community.contracts.event.EventEnvelopeParser;
import com.nowcoder.community.contracts.event.EventTopics;
import com.nowcoder.community.contracts.event.UnknownEventAction;
import com.nowcoder.community.platform.kafka.KafkaTraceSupport;
import com.nowcoder.community.content.api.event.ContentEventTypes;
import com.nowcoder.community.content.api.event.payload.CommentPayload;
import com.nowcoder.community.content.api.event.payload.PostPayload;
import com.nowcoder.community.social.projection.ContentEntityProjectionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * social-service 内容实体投影消费者（最终一致）：
 * - 输入：content-service 发布的 post/comment 事件
 * - 输出：写入本地投影（POST/COMMENT -> owner/postId/status）
 */
@Component
public class ContentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ContentEventConsumer.class);
    private static final Set<String> LOGGED_UNKNOWN_TYPES = ConcurrentHashMap.newKeySet();

    private final ObjectMapper objectMapper;
    private final ContentEntityProjectionRepository projectionRepository;
    private final MeterRegistry meterRegistry;
    private final UnknownEventAction unknownTypeAction;
    private final UnknownEventAction unsupportedVersionAction;

    public ContentEventConsumer(
            ObjectMapper objectMapper,
            ContentEntityProjectionRepository projectionRepository,
            MeterRegistry meterRegistry,
            @Value("${community.kafka.consumer.unknown-type-action:SKIP}") String unknownTypeAction,
            @Value("${community.kafka.consumer.unsupported-version-action:DLQ}") String unsupportedVersionAction
    ) {
        this.objectMapper = objectMapper;
        this.projectionRepository = projectionRepository;
        this.meterRegistry = meterRegistry;
        this.unknownTypeAction = UnknownEventAction.parseOrDefault(unknownTypeAction, UnknownEventAction.SKIP);
        this.unsupportedVersionAction = UnknownEventAction.parseOrDefault(unsupportedVersionAction, UnknownEventAction.DLQ);
    }

    @KafkaListener(topics = {EventTopics.POST_EVENTS_V1, EventTopics.COMMENT_EVENTS_V1}, groupId = "social-service-projection")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        KafkaTraceSupport.runWithTraceId(objectMapper, record.value(), () -> handleRecord(record));
        ack.acknowledge();
    }

    void handleRecord(ConsumerRecord<String, String> record) {
        EventEnvelopeParser.ParsedEnvelope env = EventEnvelopeParser.parse(objectMapper, record.value());
        String eventId = env.getEventId();
        String type = env.getType();
        int version = env.getVersion();

        if (version != 1) {
            count(type, "skip_unsupported_version");
            if (unsupportedVersionAction == UnknownEventAction.SKIP) {
                log.warn("skip unsupported envelope version: {}, eventId={}, type={}", version, eventId, type);
                return;
            }
            throw new IllegalArgumentException("unsupported envelope version: " + version);
        }

        Instant occurredAt = env.getOccurredAt();
        Date ts = Date.from(occurredAt == null ? Instant.now() : occurredAt);

        if (ContentEventTypes.POST_PUBLISHED.equals(type) || ContentEventTypes.POST_UPDATED.equals(type) || ContentEventTypes.POST_DELETED.equals(type)) {
            PostPayload p = objectMapper.convertValue(env.getPayload(), PostPayload.class);
            if (p == null || p.getPostId() <= 0) {
                count(type, "skip_invalid_payload");
                return;
            }
            projectionRepository.upsertIfNewer(
                    EntityTypes.POST,
                    p.getPostId(),
                    p.getUserId(),
                    p.getPostId(),
                    p.getStatus(),
                    ts
            );
            count(type, "success");
            return;
        }

        if (ContentEventTypes.COMMENT_CREATED.equals(type) || ContentEventTypes.COMMENT_DELETED.equals(type)) {
            CommentPayload p = objectMapper.convertValue(env.getPayload(), CommentPayload.class);
            int commentId = p == null ? 0 : p.getCommentId();
            if (commentId <= 0) {
                count(type, "skip_invalid_payload");
                return;
            }
            int status = ContentEventTypes.COMMENT_CREATED.equals(type) ? 0 : 1;
            projectionRepository.upsertIfNewer(
                    EntityTypes.COMMENT,
                    commentId,
                    p == null ? 0 : p.getUserId(),
                    p == null ? 0 : p.getPostId(),
                    status,
                    ts
            );
            count(type, "success");
            return;
        }

        count(type, "skip_unknown_type");
        if (unknownTypeAction == UnknownEventAction.SKIP) {
            if (LOGGED_UNKNOWN_TYPES.add(type)) {
                log.warn("skip unsupported event type: {}, example eventId={}", type, eventId);
            }
            return;
        }
        throw new IllegalArgumentException("unsupported event type: " + type);
    }

    private void count(String type, String outcome) {
        String t = type == null ? "" : type;
        String o = outcome == null ? "" : outcome;
        meterRegistry.counter("social_content_projection_events_total", Tags.of("type", t, "outcome", o)).increment();
    }
}
