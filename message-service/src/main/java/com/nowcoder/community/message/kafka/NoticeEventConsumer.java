package com.nowcoder.community.message.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.EventEnvelope;
import com.nowcoder.community.common.event.EventTopics;
import com.nowcoder.community.common.event.EventTypes;
import com.nowcoder.community.common.event.payload.CommentPayload;
import com.nowcoder.community.common.event.payload.FollowPayload;
import com.nowcoder.community.common.event.payload.LikePayload;
import com.nowcoder.community.message.dao.ConsumedEventMapper;
import com.nowcoder.community.message.service.NoticeService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
public class NoticeEventConsumer {

    private final ObjectMapper objectMapper;
    private final ConsumedEventMapper consumedEventMapper;
    private final NoticeService noticeService;

    public NoticeEventConsumer(ObjectMapper objectMapper, ConsumedEventMapper consumedEventMapper, NoticeService noticeService) {
        this.objectMapper = objectMapper;
        this.consumedEventMapper = consumedEventMapper;
        this.noticeService = noticeService;
    }

    @KafkaListener(topics = {EventTopics.COMMENT_EVENTS_V1, EventTopics.SOCIAL_EVENTS_V1}, groupId = "message-service")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) throws Exception {
        handleRecord(record);
        ack.acknowledge();
    }

    @Transactional
    void handleRecord(ConsumerRecord<String, String> record) throws Exception {
        JsonNode root = objectMapper.readTree(record.value());
        String eventId = text(root, "eventId");
        String type = text(root, "type");

        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId 缺失");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type 缺失");
        }

        if (consumedEventMapper.countByEventId(eventId) > 0) {
            return;
        }

        int toUserId = 0;
        String topic = null;
        Object payload = null;

        if (EventTypes.COMMENT_CREATED.equals(type)) {
            CommentPayload p = objectMapper.treeToValue(root.get("payload"), CommentPayload.class);
            payload = p;
            topic = "comment";
            toUserId = p.getTargetUserId() == null ? 0 : p.getTargetUserId();
        } else if (EventTypes.LIKE_CREATED.equals(type)) {
            LikePayload p = objectMapper.treeToValue(root.get("payload"), LikePayload.class);
            payload = p;
            topic = "like";
            toUserId = p.getEntityUserId() == null ? 0 : p.getEntityUserId();
        } else if (EventTypes.FOLLOW_CREATED.equals(type)) {
            FollowPayload p = objectMapper.treeToValue(root.get("payload"), FollowPayload.class);
            payload = p;
            topic = "follow";
            toUserId = p.getEntityUserId() == null ? 0 : p.getEntityUserId();
        } else {
            consumedEventMapper.insert(eventId);
            return;
        }

        consumedEventMapper.insert(eventId);

        if (toUserId <= 0) {
            return;
        }

        String contentJson = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId,
                "type", type,
                "payload", payload
        ));
        noticeService.createNotice(toUserId, topic, contentJson);
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

