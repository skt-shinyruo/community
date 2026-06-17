package com.nowcoder.community.growth.infrastructure.event;

import com.nowcoder.community.common.kafka.trace.TraceKafkaSender;
import com.nowcoder.community.growth.application.TaskProgressKafkaDispatchPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletionException;

@Component
@ConditionalOnClass(KafkaTemplate.class)
public class TaskProgressKafkaSenderAdapter implements TaskProgressKafkaDispatchPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TaskProgressKafkaSenderAdapter(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void send(String topic, String key, Object payload) {
        try {
            TraceKafkaSender.send(kafkaTemplate, topic, key, payload).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("growth task kafka publish failed: " + topic, cause);
        }
    }
}
