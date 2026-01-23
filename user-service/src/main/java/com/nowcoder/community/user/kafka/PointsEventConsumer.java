package com.nowcoder.community.user.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.EventTopics;
import com.nowcoder.community.common.event.EventTypes;
import com.nowcoder.community.common.event.payload.CommentPayload;
import com.nowcoder.community.common.event.payload.LikePayload;
import com.nowcoder.community.common.event.payload.PostPayload;
import com.nowcoder.community.common.kafka.KafkaTraceSupport;
import com.nowcoder.community.user.service.PointsService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 积分事件消费者（成长体系）：
 * - 输入：post/comment/social 三类事件 topic
 * - 输出：写入 user_score_log + 更新 user.score（幂等）
 */
@Component
public class PointsEventConsumer {

    private final ObjectMapper objectMapper;
    private final PointsService pointsService;

    public PointsEventConsumer(ObjectMapper objectMapper, PointsService pointsService) {
        this.objectMapper = objectMapper;
        this.pointsService = pointsService;
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
        JsonNode root = objectMapper.readTree(record.value());
        String eventId = text(root, "eventId");
        String type = text(root, "type");

        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId 缺失");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type 缺失");
        }

        if (EventTypes.POST_PUBLISHED.equals(type)) {
            PostPayload p = objectMapper.treeToValue(root.get("payload"), PostPayload.class);
            pointsService.applyPoints(p.getUserId(), eventId, type, 10);
            return;
        }

        if (EventTypes.COMMENT_CREATED.equals(type)) {
            CommentPayload p = objectMapper.treeToValue(root.get("payload"), CommentPayload.class);
            pointsService.applyPoints(p.getUserId(), eventId, type, 2);
            return;
        }

        if (EventTypes.LIKE_CREATED.equals(type)) {
            LikePayload p = objectMapper.treeToValue(root.get("payload"), LikePayload.class);
            int toUserId = p.getEntityUserId() == null ? 0 : p.getEntityUserId();
            if (toUserId > 0 && toUserId != p.getActorUserId()) {
                pointsService.applyPoints(toUserId, eventId, type, 1);
            }
        }
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String s = node.asText();
        return s == null || s.isBlank() ? null : s;
    }
}

