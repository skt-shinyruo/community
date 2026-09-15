package com.nowcoder.community.im.realtime.frame;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImUnsupportedSchemaVersionException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ImFrameCodec {

    private final JacksonJsonCodec jsonCodec;

    public ImFrameCodec(JacksonJsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    public JsonNode readTree(String text) {
        try {
            return jsonCodec.readTree(text);
        } catch (JsonCodecException e) {
            throw new IllegalArgumentException("invalid websocket frame json", e);
        }
    }

    public void requireSupportedSchemaVersion(JsonNode node) {
        JsonNode schemaVersionNode = node == null ? null : node.get("schemaVersion");
        if (schemaVersionNode == null
                || !schemaVersionNode.isIntegralNumber()
                || !schemaVersionNode.canConvertToInt()) {
            throw new ImUnsupportedSchemaVersionException(0, ImContractVersions.WS_FRAME_VERSION);
        }

        int schemaVersion = schemaVersionNode.intValue();
        if (schemaVersion != ImContractVersions.WS_FRAME_VERSION) {
            throw new ImUnsupportedSchemaVersionException(schemaVersion, ImContractVersions.WS_FRAME_VERSION);
        }
    }

    public <T> T read(JsonNode node, Class<T> type) {
        try {
            return jsonCodec.treeToValue(node, type);
        } catch (JsonCodecException e) {
            if (hasUnsupportedSchemaVersion(e)) {
                throw new ImUnsupportedSchemaVersionException(unsupportedSchemaVersion(e), supportedSchemaVersion(e));
            }
            throw new IllegalArgumentException("invalid websocket frame payload", e);
        }
    }

    /**
     * Reads a known inbound frame after checking its required fields on the raw
     * tree. Jackson's scalar coercion (string {@code "1"} to long, number to
     * string) must not repair malformed frames into valid ones, so v1 frames
     * declare exact JSON types per required field and violations fail here,
     * before any business processing.
     */
    public <T> T read(JsonNode node, Class<T> type, Map<String, FieldType> requiredFields) {
        requireFields(node, requiredFields);
        return read(node, type);
    }

    private static void requireFields(JsonNode node, Map<String, FieldType> requiredFields) {
        for (Map.Entry<String, FieldType> field : requiredFields.entrySet()) {
            JsonNode value = node == null ? null : node.get(field.getKey());
            if (!field.getValue().accepts(value)) {
                throw new IllegalArgumentException(
                        "invalid websocket frame field '" + field.getKey() + "': expected " + field.getValue());
            }
        }
    }

    /**
     * Strict JSON types for required v1 frame fields; unknown extension fields
     * stay ignored per {@link com.nowcoder.community.im.common.ImJsonContract}.
     */
    public enum FieldType {
        TEXT {
            @Override
            boolean accepts(JsonNode node) {
                return node != null && node.isTextual();
            }
        },
        LONG {
            @Override
            boolean accepts(JsonNode node) {
                return node != null && node.isIntegralNumber() && node.canConvertToLong();
            }
        };

        abstract boolean accepts(JsonNode node);
    }

    public String write(Object value) {
        try {
            return jsonCodec.toJson(value);
        } catch (JsonCodecException e) {
            throw new IllegalArgumentException("failed to encode websocket frame", e);
        }
    }

    private static boolean hasUnsupportedSchemaVersion(Throwable throwable) {
        return unsupportedSchemaVersionCause(throwable) != null;
    }

    private static int unsupportedSchemaVersion(Throwable throwable) {
        return unsupportedSchemaVersionCause(throwable).schemaVersion();
    }

    private static int supportedSchemaVersion(Throwable throwable) {
        return unsupportedSchemaVersionCause(throwable).supportedSchemaVersion();
    }

    private static ImUnsupportedSchemaVersionException unsupportedSchemaVersionCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ImUnsupportedSchemaVersionException unsupported) {
                return unsupported;
            }
            current = current.getCause();
        }
        return null;
    }
}
