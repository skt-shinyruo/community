package com.nowcoder.community.social.infrastructure.event;

import com.nowcoder.community.common.kafka.trace.TraceKafkaSender;
import com.nowcoder.community.social.application.SocialIntegrationEventDispatcher;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
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
        TraceKafkaSender.sendSync(kafkaTemplate, kafkaTopic, eventKey, event, "social event kafka publish failed");
    }
}
