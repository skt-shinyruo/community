package com.nowcoder.community.im.realtime.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

public final class WsProtocol {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WsProtocol() {
    }

    public static String authOk(UUID userId) {
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

    public static String sendAck(String cmd, String clientMsgId, String requestId) {
        return json(Map.of(
                "type", "sendAck",
                "cmd", String.valueOf(cmd),
                "clientMsgId", String.valueOf(clientMsgId),
                "requestId", String.valueOf(requestId)
        ));
    }

    public static String sendError(String cmd, String clientMsgId, String requestId, String message) {
        return sendError(cmd, clientMsgId, requestId, 503, message, "");
    }

    public static String sendError(String cmd, String clientMsgId, String requestId, int code, String message, String traceId) {
        String t = traceId == null ? "" : String.valueOf(traceId);
        return json(Map.of(
                "type", "sendError",
                "cmd", String.valueOf(cmd),
                "clientMsgId", String.valueOf(clientMsgId),
                "requestId", String.valueOf(requestId),
                "code", code,
                "message", String.valueOf(message),
                "traceId", t
        ));
    }

    private static String json(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return "{\"type\":\"error\",\"message\":\"encode_failed\"}";
        }
    }
}
