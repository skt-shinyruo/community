package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.common.kafka.trace.TraceKafkaSender;
import com.nowcoder.community.content.application.ContentIntegrationEventDispatcher;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
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
        TraceKafkaSender.sendSync(kafkaTemplate, kafkaTopic, eventKey, event, "content event kafka publish failed");
    }
}
