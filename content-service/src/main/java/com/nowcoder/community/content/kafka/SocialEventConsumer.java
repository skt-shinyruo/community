package com.nowcoder.community.content.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.domain.EntityTypes;
import com.nowcoder.community.common.event.EventEnvelopeParser;
import com.nowcoder.community.common.event.UnknownEventAction;
import com.nowcoder.community.common.kafka.KafkaTraceSupport;
import com.nowcoder.community.social.api.event.SocialEventTopics;
import com.nowcoder.community.social.api.event.SocialEventTypes;
import com.nowcoder.community.social.api.event.payload.LikePayload;
import com.nowcoder.community.content.like.LikeRedisKeys;
import com.nowcoder.community.content.score.PostScoreQueue;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SocialEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SocialEventConsumer.class);
    private static final Set<String> LOGGED_UNKNOWN_TYPES = ConcurrentHashMap.newKeySet();

    private static final int ENTITY_TYPE_POST = EntityTypes.POST;

    private final ObjectMapper objectMapper;
    private final PostScoreQueue postScoreQueue;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final boolean redisProjectionEnabled;
    private final UnknownEventAction unknownTypeAction;
    private final UnknownEventAction unsupportedVersionAction;

    public SocialEventConsumer(
            ObjectMapper objectMapper,
            PostScoreQueue postScoreQueue,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            @Value("${content.storage:redis}") String storage,
            @Value("${community.kafka.consumer.unknown-type-action:SKIP}") String unknownTypeAction,
            @Value("${community.kafka.consumer.unsupported-version-action:DLQ}") String unsupportedVersionAction
    ) {
        this.objectMapper = objectMapper;
        this.postScoreQueue = postScoreQueue;
        this.redisTemplateProvider = redisTemplateProvider;
        this.redisProjectionEnabled = "redis".equalsIgnoreCase(storage == null ? "" : storage.trim());
        this.unknownTypeAction = UnknownEventAction.parseOrDefault(unknownTypeAction, UnknownEventAction.SKIP);
        this.unsupportedVersionAction = UnknownEventAction.parseOrDefault(unsupportedVersionAction, UnknownEventAction.DLQ);
    }

    @KafkaListener(topics = SocialEventTopics.SOCIAL_EVENTS_V1, groupId = "content-service")
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

        boolean likeCreated = SocialEventTypes.LIKE_CREATED.equals(type);
        boolean likeRemoved = SocialEventTypes.LIKE_REMOVED.equals(type);
        if (!likeCreated && !likeRemoved) {
            if (unknownTypeAction == UnknownEventAction.SKIP) {
                if (LOGGED_UNKNOWN_TYPES.add(type)) {
                    log.warn("skip unsupported event type: {}, example eventId={}", type, eventId);
                }
                return;
            }
            throw new IllegalArgumentException("unsupported event type: " + type);
        }

        LikePayload payload = objectMapper.convertValue(env.getPayload(), LikePayload.class);
        if (payload == null) {
            return;
        }

        if (payload.getEntityType() != ENTITY_TYPE_POST) {
            return;
        }
        int postId = payload.getEntityId() > 0 ? payload.getEntityId() : (payload.getPostId() == null ? 0 : payload.getPostId());
        if (postId <= 0) {
            return;
        }

        // 点赞投影：用 Redis Set 作为读模型（与帖子详情/热帖分数链路统一）。
        if (redisProjectionEnabled && payload.getActorUserId() > 0) {
            StringRedisTemplate redisTemplate = redisTemplateProvider == null ? null : redisTemplateProvider.getIfAvailable();
            if (redisTemplate != null) {
                String key = LikeRedisKeys.entityKey(payload.getEntityType(), payload.getEntityId());
                String member = String.valueOf(payload.getActorUserId());
                if (likeCreated) {
                    redisTemplate.opsForSet().add(key, member);
                } else {
                    redisTemplate.opsForSet().remove(key, member);
                }
            }
        }

        postScoreQueue.add(postId);
    }
}
