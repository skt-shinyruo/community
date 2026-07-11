package com.nowcoder.community.user.infrastructure.event;

import com.nowcoder.community.common.kafka.trace.TraceKafkaSender;
import com.nowcoder.community.user.application.UserIntegrationEventDispatcher;
import com.nowcoder.community.user.contracts.event.UserContractEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletionException;

@Component
@ConditionalOnClass(KafkaTemplate.class)
public class UserEventKafkaSenderAdapter implements UserIntegrationEventDispatcher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String kafkaTopic;

    public UserEventKafkaSenderAdapter(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${user.events.kafka-topic:user.events}") String kafkaTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTopic = kafkaTopic;
    }

    @Override
    public void dispatch(String eventKey, UserContractEvent event) {
        try {
            TraceKafkaSender.send(kafkaTemplate, kafkaTopic, eventKey, event).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("user event kafka publish failed: " + kafkaTopic, cause);
        } catch (RuntimeException e) {
            throw new IllegalStateException("user event kafka publish failed: " + kafkaTopic, e);
        }
    }
}
