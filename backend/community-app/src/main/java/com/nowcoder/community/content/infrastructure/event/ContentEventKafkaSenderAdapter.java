package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.common.kafka.trace.TraceKafkaSender;
import com.nowcoder.community.content.application.ContentEventKafkaDispatchPort;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletionException;

@Component
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnExpression("'${content.events.publisher:outbox-kafka}' == 'outbox-kafka' && '${events.outbox.enabled:true}' == 'true'")
public class ContentEventKafkaSenderAdapter implements ContentEventKafkaDispatchPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ContentEventKafkaSenderAdapter(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void send(String topic, String key, ContentContractEvent event) {
        try {
            TraceKafkaSender.send(kafkaTemplate, topic, key, event).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("content event kafka publish failed: " + topic, cause);
        } catch (RuntimeException e) {
            throw new IllegalStateException("content event kafka publish failed: " + topic, e);
        }
    }
}
