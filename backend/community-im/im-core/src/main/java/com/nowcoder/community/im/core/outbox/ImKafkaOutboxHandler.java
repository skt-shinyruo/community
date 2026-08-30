package com.nowcoder.community.im.core.outbox;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.kafka.trace.TraceKafkaSender;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.StringUtils;

public class ImKafkaOutboxHandler<T> implements OutboxHandler {

    private final String topic;
    private final Class<T> payloadType;
    private final JacksonJsonCodec jsonCodec;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ImKafkaOutboxHandler(
            String topic,
            Class<T> payloadType,
            JacksonJsonCodec jsonCodec,
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        this.topic = topic;
        this.payloadType = payloadType;
        this.jsonCodec = jsonCodec;
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
            payload = jsonCodec.fromJson(event.payload(), payloadType);
        } catch (JsonCodecException e) {
            throw new IllegalStateException("IM outbox payload deserialization failed: " + topic, e);
        }
        TraceKafkaSender.sendSync(kafkaTemplate, topic, event.eventKey(), payload, "IM outbox kafka publish failed");
    }
}
