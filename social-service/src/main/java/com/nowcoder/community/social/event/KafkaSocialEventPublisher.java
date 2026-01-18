package com.nowcoder.community.social.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.EventEnvelope;
import com.nowcoder.community.common.event.EventTopics;
import com.nowcoder.community.common.event.EventTypes;
import com.nowcoder.community.common.event.payload.FollowPayload;
import com.nowcoder.community.common.event.payload.LikePayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "social.events.publisher", havingValue = "kafka", matchIfMissing = true)
public class KafkaSocialEventPublisher implements SocialEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaSocialEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishLikeCreated(LikePayload payload) {
        publish(EventTypes.LIKE_CREATED, "like:" + payload.getEntityType() + ":" + payload.getEntityId(), payload);
    }

    @Override
    public void publishFollowCreated(FollowPayload payload) {
        publish(EventTypes.FOLLOW_CREATED, "follow:" + payload.getEntityType() + ":" + payload.getEntityId(), payload);
    }

    private void publish(String type, String key, Object payload) {
        try {
            EventEnvelope<Object> envelope = EventEnvelope.of(type, 1, "social-service", payload);
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(EventTopics.SOCIAL_EVENTS_V1, key, json);
        } catch (Exception e) {
            throw new IllegalStateException("发布事件失败: " + type, e);
        }
    }
}

