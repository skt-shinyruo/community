package com.nowcoder.community.content.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.EventEnvelope;
import com.nowcoder.community.common.event.EventTopics;
import com.nowcoder.community.common.event.EventTypes;
import com.nowcoder.community.common.event.payload.CommentPayload;
import com.nowcoder.community.common.event.payload.PostPayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "content.events.publisher", havingValue = "kafka", matchIfMissing = true)
public class KafkaContentEventPublisher implements ContentEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaContentEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
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

    private void publish(String topic, String type, String key, Object payload) {
        try {
            EventEnvelope<Object> envelope = EventEnvelope.of(type, 1, "content-service", payload);
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(topic, key, json);
        } catch (Exception e) {
            throw new IllegalStateException("发布事件失败: " + type, e);
        }
    }
}
