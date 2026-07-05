package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.common.kafka.trace.TraceKafkaSender;
import com.nowcoder.community.content.application.ContentIntegrationEventDispatcher;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletionException;

@Component
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnExpression("'${content.events.publisher:outbox-kafka}' == 'outbox-kafka' && '${events.outbox.enabled:true}' == 'true'")
public class ContentEventKafkaSenderAdapter implements ContentIntegrationEventDispatcher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String kafkaTopic;

    public ContentEventKafkaSenderAdapter(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${content.events.kafka-topic:content.events}") String kafkaTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTopic = kafkaTopic;
    }

    @Override
    public void dispatch(String eventKey, ContentContractEvent event) {
        try {
            TraceKafkaSender.send(kafkaTemplate, kafkaTopic, eventKey, event).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("content event kafka publish failed: " + kafkaTopic, cause);
        } catch (RuntimeException e) {
            throw new IllegalStateException("content event kafka publish failed: " + kafkaTopic, e);
        }
    }
}
