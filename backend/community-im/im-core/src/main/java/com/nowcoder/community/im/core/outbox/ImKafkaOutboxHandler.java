package com.nowcoder.community.im.core.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.kafka.trace.TraceKafkaSender;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.StringUtils;

import java.util.concurrent.CompletionException;

public class ImKafkaOutboxHandler<T> implements OutboxHandler {

    private final String topic;
    private final Class<T> payloadType;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ImKafkaOutboxHandler(
            String topic,
            Class<T> payloadType,
            ObjectMapper objectMapper,
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        this.topic = topic;
        this.payloadType = payloadType;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public String topic() {
        return topic;
    }

    @Override
    public void handle(OutboxEvent event) {
        if (event == null || !StringUtils.hasText(event.payload())) {
            return;
        }
        T payload;
        try {
            payload = objectMapper.readValue(event.payload(), payloadType);
        } catch (Exception e) {
            throw new IllegalStateException("IM outbox payload deserialization failed: " + topic, e);
        }
        try {
            TraceKafkaSender.send(kafkaTemplate, topic, event.eventKey(), payload).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("IM outbox kafka publish failed: " + topic, cause);
        }
    }
}
