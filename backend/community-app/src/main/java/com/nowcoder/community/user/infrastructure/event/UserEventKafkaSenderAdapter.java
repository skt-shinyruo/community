package com.nowcoder.community.user.infrastructure.event;

import com.nowcoder.community.common.kafka.trace.TraceKafkaSender;
import com.nowcoder.community.user.application.UserIntegrationEventDispatcher;
import com.nowcoder.community.user.contracts.event.UserContractEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
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
        TraceKafkaSender.sendSync(kafkaTemplate, kafkaTopic, eventKey, event, "user event kafka publish failed");
    }
}
