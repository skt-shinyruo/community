package com.nowcoder.community.contracts.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * 事件 envelope 解析与校验工具：用于消费端统一校验 required fields/version/type。
 *
 * <p>约定：当 required fields 缺失或 version 非法时，抛出异常交给上层消费框架处理（重试/死信/告警），以保持 fail-closed。</p>
 */
public final class EventEnvelopeParser {

    private EventEnvelopeParser() {
    }

    public static ParsedEnvelope parse(ObjectMapper objectMapper, String json) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper 缺失");
        }
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("record value 为空");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("record value 非法 JSON", e);
        }
        String eventId = text(root, "eventId");
        String type = text(root, "type");
        int version = root.path("version").asInt(0);
        String traceId = text(root, "traceId");
        String producer = text(root, "producer");
        Instant occurredAt = parseInstant(text(root, "occurredAt"));
        JsonNode payload = root.get("payload");

        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId 缺失");
        }
        if (type == null || type.isBlank()) {
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
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(s.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String text(JsonNode root, String field) {
        if (root == null || field == null || field.isBlank()) {
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
