package com.nowcoder.community.im.infrastructure.event;

import com.nowcoder.community.common.kafka.trace.TraceKafkaSender;
import com.nowcoder.community.im.application.ImPolicyEventKafkaDispatchPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletionException;

@Component
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class ImPolicyEventKafkaSenderAdapter implements ImPolicyEventKafkaDispatchPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ImPolicyEventKafkaSenderAdapter(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void send(String topic, String key, Object event) {
        try {
            TraceKafkaSender.send(kafkaTemplate, topic, key, event).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("im policy kafka publish failed: " + topic, cause);
        }
    }
}
