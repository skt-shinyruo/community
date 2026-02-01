package com.nowcoder.community.user.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.EventEnvelopeParser;
import com.nowcoder.community.common.event.EventTopics;
import com.nowcoder.community.common.event.EventTypes;
import com.nowcoder.community.common.event.UnknownEventAction;
import com.nowcoder.community.common.event.payload.CommentPayload;
import com.nowcoder.community.common.event.payload.LikePayload;
import com.nowcoder.community.common.event.payload.PostPayload;
import com.nowcoder.community.common.kafka.KafkaTraceSupport;
import com.nowcoder.community.user.service.PointsService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 积分事件消费者（成长体系）：
 * - 输入：post/comment/social 三类事件 topic
 * - 输出：写入 user_score_log + 更新 user.score（幂等）
 */
@Component
public class PointsEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PointsEventConsumer.class);
    private static final Set<String> LOGGED_UNKNOWN_TYPES = ConcurrentHashMap.newKeySet();

    private final ObjectMapper objectMapper;
    private final PointsService pointsService;
    private final UnknownEventAction unknownTypeAction;
    private final UnknownEventAction unsupportedVersionAction;

    public PointsEventConsumer(
            ObjectMapper objectMapper,
            PointsService pointsService,
            @Value("${community.kafka.consumer.unknown-type-action:SKIP}") String unknownTypeAction,
            @Value("${community.kafka.consumer.unsupported-version-action:DLQ}") String unsupportedVersionAction
    ) {
        this.objectMapper = objectMapper;
        this.pointsService = pointsService;
        this.unknownTypeAction = UnknownEventAction.parseOrDefault(unknownTypeAction, UnknownEventAction.SKIP);
        this.unsupportedVersionAction = UnknownEventAction.parseOrDefault(unsupportedVersionAction, UnknownEventAction.DLQ);
    }

    @KafkaListener(topics = {EventTopics.POST_EVENTS_V1, EventTopics.COMMENT_EVENTS_V1, EventTopics.SOCIAL_EVENTS_V1}, groupId = "user-service")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) throws Exception {
        KafkaTraceSupport.runWithTraceId(objectMapper, record.value(), () -> handleRecord(record));
        ack.acknowledge();
    }

    /**
     * 供测试/手动调用：仅在处理成功后由上层 ack。
     */
    public void handleRecord(ConsumerRecord<String, String> record) throws Exception {
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

        if (EventTypes.POST_PUBLISHED.equals(type)) {
            PostPayload p = objectMapper.treeToValue(env.getPayload(), PostPayload.class);
            pointsService.applyPoints(p.getUserId(), eventId, type, 10);
            return;
        }

        if (EventTypes.COMMENT_CREATED.equals(type)) {
            CommentPayload p = objectMapper.treeToValue(env.getPayload(), CommentPayload.class);
            pointsService.applyPoints(p.getUserId(), eventId, type, 2);
            return;
        }

        if (EventTypes.LIKE_CREATED.equals(type)) {
            LikePayload p = objectMapper.treeToValue(env.getPayload(), LikePayload.class);
            int toUserId = p.getEntityUserId() == null ? 0 : p.getEntityUserId();
            if (toUserId > 0 && toUserId != p.getActorUserId()) {
                pointsService.applyPoints(toUserId, eventId, type, 1);
            }
            return;
        }

        if (EventTypes.LIKE_REMOVED.equals(type)) {
            LikePayload p = objectMapper.treeToValue(env.getPayload(), LikePayload.class);
            int toUserId = p.getEntityUserId() == null ? 0 : p.getEntityUserId();
            if (toUserId > 0 && toUserId != p.getActorUserId()) {
                pointsService.applyPoints(toUserId, eventId, type, -1);
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
