package com.nowcoder.community.im.gateway.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import org.springframework.stereotype.Component;

@Component
public class ImGatewayFrameCodec {

    private final JsonCodec jsonCodec;

    public ImGatewayFrameCodec(JsonCodec jsonCodec) {
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
}
