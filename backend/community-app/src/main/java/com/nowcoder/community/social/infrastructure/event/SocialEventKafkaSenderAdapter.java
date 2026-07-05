package com.nowcoder.community.social.infrastructure.event;

import com.nowcoder.community.common.kafka.trace.TraceKafkaSender;
import com.nowcoder.community.social.application.SocialIntegrationEventDispatcher;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletionException;

@Component
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnExpression("'${social.events.publisher:outbox-kafka}' == 'outbox-kafka' && '${events.outbox.enabled:true}' == 'true'")
public class SocialEventKafkaSenderAdapter implements SocialIntegrationEventDispatcher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String kafkaTopic;

    public SocialEventKafkaSenderAdapter(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${social.events.kafka-topic:social.events}") String kafkaTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTopic = kafkaTopic;
    }

    @Override
    public void dispatch(String eventKey, SocialContractEvent event) {
        try {
            TraceKafkaSender.send(kafkaTemplate, kafkaTopic, eventKey, event).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("social event kafka publish failed: " + kafkaTopic, cause);
        } catch (RuntimeException e) {
            throw new IllegalStateException("social event kafka publish failed: " + kafkaTopic, e);
        }
    }
}
