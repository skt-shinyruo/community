package com.nowcoder.community.message.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.EventEnvelopeParser;
import com.nowcoder.community.common.event.UnknownEventAction;
import com.nowcoder.community.common.kafka.KafkaTraceSupport;
import com.nowcoder.community.content.api.event.ContentEventTopics;
import com.nowcoder.community.social.api.event.SocialEventTopics;
import com.nowcoder.community.social.api.event.SocialEventTypes;
import com.nowcoder.community.social.api.event.payload.BlockPayload;
import com.nowcoder.community.user.api.event.UserEventTypes;
import com.nowcoder.community.user.api.event.payload.ModerationStatusPayload;
import com.nowcoder.community.message.projection.UserModerationProjectionRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * message-service 投影更新消费者（最终一致）：
 * - 用户处罚状态（来自 user-service ModerationStatusChanged）
 * - 拉黑关系（来自 social-service BlockRelationChanged）
 */
@Component
public class ModerationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ModerationEventConsumer.class);
    private static final Set<String> LOGGED_UNKNOWN_TYPES = ConcurrentHashMap.newKeySet();

    private final ObjectMapper objectMapper;
    private final UserModerationProjectionRepository projectionRepository;
    private final UnknownEventAction unknownTypeAction;
    private final UnknownEventAction unsupportedVersionAction;

    public ModerationEventConsumer(
            ObjectMapper objectMapper,
            UserModerationProjectionRepository projectionRepository,
            @Value("${community.kafka.consumer.unknown-type-action:SKIP}") String unknownTypeAction,
            @Value("${community.kafka.consumer.unsupported-version-action:DLQ}") String unsupportedVersionAction
    ) {
        this.objectMapper = objectMapper;
        this.projectionRepository = projectionRepository;
        this.unknownTypeAction = UnknownEventAction.parseOrDefault(unknownTypeAction, UnknownEventAction.SKIP);
        this.unsupportedVersionAction = UnknownEventAction.parseOrDefault(unsupportedVersionAction, UnknownEventAction.DLQ);
    }

    @KafkaListener(topics = {ContentEventTopics.MODERATION_EVENTS_V1, SocialEventTopics.SOCIAL_EVENTS_V1}, groupId = "message-service-projection")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        KafkaTraceSupport.runWithTraceId(
                objectMapper,
                record.value(),
                () -> handleRecord(record)
        );
        ack.acknowledge();
    }

    void handleRecord(ConsumerRecord<String, String> record) {
        EventEnvelopeParser.ParsedEnvelope env = EventEnvelopeParser.parse(objectMapper, record.value());
        String eventId = env.getEventId();
        String type = env.getType();
        int version = env.getVersion();

        if (version != 1) {
            if (unsupportedVersionAction == UnknownEventAction.SKIP) {
                log.warn("skip unsupported envelope version: {}, eventId={}, type={}", version, eventId, type);
                return;
            }
            throw new IllegalArgumentException("unsupported envelope version: " + version);
        }

        Instant occurredAt = env.getOccurredAt();
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }

        if (UserEventTypes.MODERATION_STATUS_CHANGED.equals(type)) {
            ModerationStatusPayload p = objectMapper.convertValue(env.getPayload(), ModerationStatusPayload.class);
            int userId = p == null || p.getUserId() == null ? 0 : p.getUserId();
            if (userId > 0) {
                projectionRepository.upsertModerationStatus(userId, p.getMuteUntil(), p.getBanUntil(), occurredAt);
            }
            return;
        }

        if (SocialEventTypes.BLOCK_RELATION_CHANGED.equals(type)) {
            BlockPayload p = objectMapper.convertValue(env.getPayload(), BlockPayload.class);
            int a = p == null || p.getBlockerUserId() == null ? 0 : p.getBlockerUserId();
            int b = p == null || p.getBlockedUserId() == null ? 0 : p.getBlockedUserId();
            boolean blocked = p != null && Boolean.TRUE.equals(p.getBlocked());
            if (a > 0 && b > 0 && a != b) {
                projectionRepository.upsertBlockRelation(a, b, blocked, occurredAt);
            }
            return;
        }

        if (unknownTypeAction == UnknownEventAction.SKIP) {
            if (LOGGED_UNKNOWN_TYPES.add(type)) {
                log.warn("skip unsupported event type: {}, example eventId={}", type, eventId);
            }
            return;
        }
        throw new IllegalArgumentException("unsupported event type: " + type);
    }
}
