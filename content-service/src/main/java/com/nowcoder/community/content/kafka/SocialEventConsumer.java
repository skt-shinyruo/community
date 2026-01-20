package com.nowcoder.community.content.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.EventTopics;
import com.nowcoder.community.common.event.EventTypes;
import com.nowcoder.community.common.event.payload.LikePayload;
import com.nowcoder.community.common.kafka.KafkaTraceSupport;
import com.nowcoder.community.content.score.PostScoreQueue;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class SocialEventConsumer {

    private static final int ENTITY_TYPE_POST = 1;

    private final ObjectMapper objectMapper;
    private final PostScoreQueue postScoreQueue;

    public SocialEventConsumer(ObjectMapper objectMapper, PostScoreQueue postScoreQueue) {
        this.objectMapper = objectMapper;
        this.postScoreQueue = postScoreQueue;
    }

    @KafkaListener(topics = EventTopics.SOCIAL_EVENTS_V1, groupId = "content-service")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) throws Exception {
        KafkaTraceSupport.runWithTraceId(
                objectMapper,
                record.value(),
                (KafkaTraceSupport.ThrowingRunnable) () -> handleRecord(record)
        );
        ack.acknowledge();
    }

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

        if (!EventTypes.LIKE_CREATED.equals(type)) {
            return;
        }

        LikePayload payload = objectMapper.treeToValue(root.get("payload"), LikePayload.class);
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
        postScoreQueue.add(postId);
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
