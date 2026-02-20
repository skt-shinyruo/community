package com.nowcoder.community.common.kafka.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.nowcoder.community.common.event.EventTopicConventions;
import com.nowcoder.community.common.kafka.KafkaTraceSupport;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Kafka DLQ 发布器（fail-closed）：
 * - DLQ send 必须可确认（acknowledged），失败则抛出异常阻断 offset commit
 * - schema 统一，包含 traceId 与原始 offset 信息
 */
public class KafkaDlqPublisher {

    private static final int DEFAULT_MAX_PAYLOAD_CHARS = 10_000;
    private static final int DEFAULT_MAX_ERROR_MESSAGE_CHARS = 2_000;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Duration sendTimeout;
    private final int maxPayloadChars;
    private final int maxErrorMessageChars;

    public KafkaDlqPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this(kafkaTemplate, objectMapper, Duration.ofSeconds(3), DEFAULT_MAX_PAYLOAD_CHARS, DEFAULT_MAX_ERROR_MESSAGE_CHARS);
    }

    public KafkaDlqPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            Duration sendTimeout,
            int maxPayloadChars,
            int maxErrorMessageChars
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate");
        this.objectMapper = objectMapper;
        this.sendTimeout = sendTimeout == null ? Duration.ofSeconds(3) : sendTimeout;
        this.maxPayloadChars = Math.max(256, maxPayloadChars);
        this.maxErrorMessageChars = Math.max(256, maxErrorMessageChars);
    }

    public void publish(ConsumerRecord<?, ?> record, Exception ex) {
        if (record == null || ex == null) {
            throw new IllegalArgumentException("record/ex must not be null");
        }

        String key = record.key() == null ? null : record.key().toString();
        String payload = record.value() == null ? null : record.value().toString();

        KafkaDlqRecord dlq = new KafkaDlqRecord();
        dlq.setOriginalTopic(record.topic());
        dlq.setOriginalPartition(record.partition());
        dlq.setOriginalOffset(record.offset());
        dlq.setKey(key);
        dlq.setPayload(truncate(payload, maxPayloadChars));
        dlq.setErrorType(ex.getClass().getName());
        dlq.setErrorMessage(truncate(ex.getMessage(), maxErrorMessageChars));
        dlq.setFailedAt(Instant.now());

        String traceId = KafkaTraceSupport.resolveTraceId(objectMapper, payload);
        dlq.setTraceId(traceId);

        String dlqTopic = record.topic() + EventTopicConventions.DLQ_SUFFIX;

        // 先尽量写 schema 化的 JSON；序列化失败再回退到原始 payload（仍保持 fail-closed）。
        String body = null;
        try {
            if (objectMapper != null) {
                body = objectMapper.writeValueAsString(dlq);
            }
        } catch (JsonProcessingException ignore) {
            body = null;
        }
        if (!StringUtils.hasText(body)) {
            body = dlq.getPayload();
        }

        try {
            kafkaTemplate.send(dlqTopic, key, body)
                    .get(Math.max(1, sendTimeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("DLQ publish interrupted: topic=" + dlqTopic + ", originalTopic=" + record.topic(), e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new RuntimeException("DLQ publish failed: topic=" + dlqTopic + ", originalTopic=" + record.topic(), cause);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new RuntimeException("DLQ publish timeout: topic=" + dlqTopic + ", originalTopic=" + record.topic(), e);
        } catch (RuntimeException e) {
            // fail-closed：DLQ 发布失败不得提交 offset，否则会造成“失败即丢”窗口
            throw new RuntimeException("DLQ publish failed: topic=" + dlqTopic + ", originalTopic=" + record.topic(), e);
        }
    }

    private String truncate(String s, int maxChars) {
        if (!StringUtils.hasText(s)) {
            return s;
        }
        String text = s;
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...(truncated)";
    }
}
