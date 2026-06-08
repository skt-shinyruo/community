package com.nowcoder.community.growth.infrastructure.event;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.kafka.trace.TraceKafkaSender;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.CompletionException;

@Component
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class CommentTaskProgressKafkaOutboxHandler implements OutboxHandler {

    private final JsonCodec jsonCodec;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String outboxTopic;
    private final String kafkaTopic;

    public CommentTaskProgressKafkaOutboxHandler(
            JsonCodec jsonCodec,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${growth.task.outbox.comment-topic:projection.growth.task.comment}") String outboxTopic,
            @Value("${growth.task.kafka.topics.comment-created:growth.task.comment-created}") String kafkaTopic
    ) {
        this.jsonCodec = jsonCodec;
        this.kafkaTemplate = kafkaTemplate;
        this.outboxTopic = outboxTopic;
        this.kafkaTopic = kafkaTopic;
    }

    @Override
    public String topic() {
        return outboxTopic;
    }

    @Override
    public void handle(OutboxEvent event) {
        if (event == null || !StringUtils.hasText(event.payload())) {
            return;
        }

        CommentPayload payload;
        try {
            payload = jsonCodec.fromJson(event.payload(), CommentPayload.class);
        } catch (JsonCodecException e) {
            throw new IllegalStateException("growth task comment outbox payload 反序列化失败", e);
        }

        if (payload.getCommentId() == null || payload.getUserId() == null || payload.getCreateTime() == null) {
            return;
        }
        sendToKafka(event, payload);
    }

    private void sendToKafka(OutboxEvent event, CommentPayload payload) {
        String key = StringUtils.hasText(event.eventKey()) ? event.eventKey().trim() : payload.getUserId().toString();
        try {
            TraceKafkaSender.send(kafkaTemplate, kafkaTopic, key, payload).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("growth task comment kafka publish failed: " + kafkaTopic, cause);
        }
    }
}
