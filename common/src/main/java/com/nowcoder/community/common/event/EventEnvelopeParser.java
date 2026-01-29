package com.nowcoder.community.common.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * 事件 envelope 解析与校验工具：用于消费端统一校验 required fields/version/type。
 *
 * <p>约定：当 required fields 缺失或 version 非法时，抛出异常交给 Kafka error handler（重试/DLQ）。</p>
 */
public final class EventEnvelopeParser {

    private EventEnvelopeParser() {
    }

    public static ParsedEnvelope parse(ObjectMapper objectMapper, String json) throws Exception {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper 缺失");
        }
        if (!StringUtils.hasText(json)) {
            throw new IllegalArgumentException("record value 为空");
        }
        JsonNode root = objectMapper.readTree(json);
        String eventId = text(root, "eventId");
        String type = text(root, "type");
        int version = root.path("version").asInt(0);
        String traceId = text(root, "traceId");
        String producer = text(root, "producer");
        Instant occurredAt = parseInstant(text(root, "occurredAt"));
        JsonNode payload = root.get("payload");

        if (!StringUtils.hasText(eventId)) {
            throw new IllegalArgumentException("eventId 缺失");
        }
        if (!StringUtils.hasText(type)) {
            throw new IllegalArgumentException("type 缺失");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("version 缺失");
        }
        if (payload == null || payload.isNull()) {
            // payload 允许为空的场景较少；默认按 fail-closed 处理，避免消费者产生不可预测行为。
            throw new IllegalArgumentException("payload 缺失");
        }

        ParsedEnvelope e = new ParsedEnvelope();
        e.eventId = eventId;
        e.type = type;
        e.version = version;
        e.traceId = traceId;
        e.producer = producer;
        e.occurredAt = occurredAt;
        e.payload = payload;
        return e;
    }

    private static Instant parseInstant(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return Instant.parse(s.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String text(JsonNode root, String field) {
        if (root == null || !StringUtils.hasText(field)) {
            return null;
        }
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String s = node.asText();
        return s == null || s.isBlank() ? null : s;
    }

    public static class ParsedEnvelope {
        private String eventId;
        private String type;
        private int version;
        private String traceId;
        private Instant occurredAt;
        private String producer;
        private JsonNode payload;

        public String getEventId() {
            return eventId;
        }

        public String getType() {
            return type;
        }

        public int getVersion() {
            return version;
        }

        public String getTraceId() {
            return traceId;
        }

        public Instant getOccurredAt() {
            return occurredAt;
        }

        public String getProducer() {
            return producer;
        }

        public JsonNode getPayload() {
            return payload;
        }
    }
}

