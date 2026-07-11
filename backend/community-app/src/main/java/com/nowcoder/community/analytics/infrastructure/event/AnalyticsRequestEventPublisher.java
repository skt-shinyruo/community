package com.nowcoder.community.analytics.infrastructure.event;

import com.nowcoder.community.analytics.application.AnalyticsRequestCapturePort;
import com.nowcoder.community.analytics.application.command.RecordRequestCommand;
import com.nowcoder.community.analytics.infrastructure.event.AnalyticsRequestKafkaListener.AnalyticsRequestEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "analytics.ingest", name = {"enabled", "async-enabled"}, havingValue = "true")
public class AnalyticsRequestEventPublisher implements AnalyticsRequestCapturePort {

    private final KafkaTemplate<String, AnalyticsRequestEvent> kafkaTemplate;
    private final String topic;

    public AnalyticsRequestEventPublisher(
            KafkaTemplate<String, AnalyticsRequestEvent> kafkaTemplate,
            @Value("${analytics.ingest.kafka-topic:analytics.request}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(AnalyticsRequestEvent event) {
        if (event == null) {
            return;
        }
        kafkaTemplate.send(topic, event.userId() == null ? "anonymous" : event.userId().toString(), event);
    }

    @Override
    public void publish(RecordRequestCommand command) {
        if (command == null) {
            return;
        }
        publish(new AnalyticsRequestEvent(command.ip(), command.userId(), command.recordUv(), command.recordDau()));
    }
}
