package com.nowcoder.community.im.realtime.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public final class WsProtocol {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WsProtocol() {
    }

    public static String authOk(int userId) {
        return json(Map.of("type", "auth_ok", "userId", userId));
    }

    public static String authError(String message) {
        return json(Map.of("type", "auth_error", "message", String.valueOf(message)));
    }

    public static String error(String message) {
        return json(Map.of("type", "error", "message", String.valueOf(message)));
    }

    public static String pong() {
        return json(Map.of("type", "pong"));
    }

    private static String json(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return "{\"type\":\"error\",\"message\":\"encode_failed\"}";
        }
    }
}

