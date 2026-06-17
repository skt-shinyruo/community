package com.nowcoder.community.user.infrastructure.event;

import com.nowcoder.community.common.kafka.trace.TraceKafkaSender;
import com.nowcoder.community.user.application.UserEventKafkaDispatchPort;
import com.nowcoder.community.user.contracts.event.UserContractEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletionException;

@Component
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnExpression("'${user.events.publisher:outbox-kafka}' == 'outbox-kafka' && '${events.outbox.enabled:true}' == 'true'")
public class UserEventKafkaSenderAdapter implements UserEventKafkaDispatchPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public UserEventKafkaSenderAdapter(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void send(String topic, String key, UserContractEvent event) {
        try {
            TraceKafkaSender.send(kafkaTemplate, topic, key, event).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("user event kafka publish failed: " + topic, cause);
        } catch (RuntimeException e) {
            throw new IllegalStateException("user event kafka publish failed: " + topic, e);
        }
    }
}
