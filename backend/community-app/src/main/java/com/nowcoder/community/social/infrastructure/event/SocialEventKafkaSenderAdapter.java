package com.nowcoder.community.social.infrastructure.event;

import com.nowcoder.community.common.kafka.trace.TraceKafkaSender;
import com.nowcoder.community.social.application.SocialEventKafkaDispatchPort;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletionException;

@Component
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnExpression("'${social.events.publisher:outbox-kafka}' == 'outbox-kafka' && '${events.outbox.enabled:true}' == 'true'")
public class SocialEventKafkaSenderAdapter implements SocialEventKafkaDispatchPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public SocialEventKafkaSenderAdapter(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void send(String topic, String key, SocialContractEvent event) {
        try {
            TraceKafkaSender.send(kafkaTemplate, topic, key, event).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("social event kafka publish failed: " + topic, cause);
        } catch (RuntimeException e) {
            throw new IllegalStateException("social event kafka publish failed: " + topic, e);
        }
    }
}
