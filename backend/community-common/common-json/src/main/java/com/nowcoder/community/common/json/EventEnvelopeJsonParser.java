package com.nowcoder.community.common.json;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.format.DateTimeParseException;

public final class EventEnvelopeJsonParser {

    private EventEnvelopeJsonParser() {
    }

    public static ParsedEnvelope parse(JsonCodec jsonCodec, String json) {
        if (jsonCodec == null) {
            throw new IllegalArgumentException("jsonCodec 缺失");
        }
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("record value 为空");
        }
        JsonNode root;
        try {
            root = jsonCodec.readTree(json);
        } catch (JsonCodecException e) {
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
