package com.nowcoder.community.common.json.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JacksonJsonCodec;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.UUID;

public final class JacksonEventPayloadSupport {

    private final JacksonJsonCodec jsonCodec;
    private final String owner;

    public JacksonEventPayloadSupport(JacksonJsonCodec jsonCodec, String owner) {
        this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec must not be null");
        this.owner = Objects.requireNonNull(owner, "owner must not be null");
    }

    public <T> T convert(String type, JsonNode payload, Class<T> payloadType) {
        try {
            return jsonCodec.treeToValue(payload, payloadType);
        } catch (RuntimeException error) {
            throw malformed(type, "payload", error);
        }
    }

    public void requireObjectPayload(String type, JsonNode payload) {
        if (payload == null || payload.isNull()) {
            throw new IllegalArgumentException(owner + " event outbox payload missing payload: " + type);
        }
        if (!payload.isObject()) {
            throw malformed(type, "payload", null);
        }
    }

    public void requireUuid(String type, JsonNode payload, String fieldName) {
        JsonNode value = payload.get(fieldName);
        if (value == null || !value.isTextual() || value.textValue() == null || value.textValue().isBlank()) {
            throw malformed(type, fieldName, null);
        }
        try {
            UUID.fromString(value.textValue());
        } catch (IllegalArgumentException error) {
            throw malformed(type, fieldName, error);
        }
    }

    public void requireBoolean(String type, JsonNode payload, String fieldName) {
        JsonNode value = payload.get(fieldName);
        if (value == null || !value.isBoolean()) {
            throw malformed(type, fieldName, null);
        }
    }

    public String text(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    public UUID uuid(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException(owner + " event outbox payload invalid " + fieldName, error);
        }
    }

    public Instant instant(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException error) {
            throw new IllegalStateException(owner + " event outbox payload invalid " + fieldName, error);
        }
    }

    public long number(JsonNode node, String fieldName) {
        if (node == null) {
            return 0L;
        }
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? 0L : value.asLong(0L);
    }

    private IllegalArgumentException malformed(String type, String fieldName, Throwable cause) {
        String message = "invalid " + owner + " event payload: type=" + type + ", field=" + fieldName;
        return cause == null ? new IllegalArgumentException(message) : new IllegalArgumentException(message, cause);
    }
}
