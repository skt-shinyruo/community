package com.nowcoder.community.im.infrastructure.event;

import com.nowcoder.community.common.kafka.trace.TraceKafkaSender;
import com.nowcoder.community.im.application.ImPolicyIntegrationEventDispatcher;
import com.nowcoder.community.im.common.event.UserBlockRelationChanged;
import com.nowcoder.community.im.common.event.UserMessagingPolicyChanged;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletionException;

@Component
@ConditionalOnClass(KafkaTemplate.class)
public class ImPolicyEventKafkaSenderAdapter implements ImPolicyIntegrationEventDispatcher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String userMessagingPolicyChangedTopic;
    private final String userBlockRelationChangedTopic;

    public ImPolicyEventKafkaSenderAdapter(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${im.kafka.topics.event-user-messaging-policy-changed:im.event.user-messaging-policy-changed}") String userMessagingPolicyChangedTopic,
            @Value("${im.kafka.topics.event-user-block-relation-changed:im.event.user-block-relation-changed}") String userBlockRelationChangedTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.userMessagingPolicyChangedTopic = userMessagingPolicyChangedTopic;
        this.userBlockRelationChangedTopic = userBlockRelationChangedTopic;
    }

    @Override
    public void dispatchUserMessagingPolicyChanged(String eventKey, UserMessagingPolicyChanged event) {
        send(userMessagingPolicyChangedTopic, eventKey, event);
    }

    @Override
    public void dispatchUserBlockRelationChanged(String eventKey, UserBlockRelationChanged event) {
        send(userBlockRelationChangedTopic, eventKey, event);
    }

    private void send(String topic, String eventKey, Object event) {
        try {
            TraceKafkaSender.send(kafkaTemplate, topic, eventKey, event).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("im policy kafka publish failed: " + topic, cause);
        }
    }
}
