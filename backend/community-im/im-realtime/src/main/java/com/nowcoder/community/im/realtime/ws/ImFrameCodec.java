package com.nowcoder.community.im.realtime.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.im.common.ImUnsupportedSchemaVersionException;
import org.springframework.stereotype.Component;

@Component
public class ImFrameCodec {

    private final JsonCodec jsonCodec;

    public ImFrameCodec(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    public JsonNode readTree(String text) {
        try {
            return jsonCodec.readTree(text);
        } catch (JsonCodecException e) {
            throw new IllegalArgumentException("invalid websocket frame json", e);
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
