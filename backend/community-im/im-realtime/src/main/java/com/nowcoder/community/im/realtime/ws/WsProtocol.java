package com.nowcoder.community.im.realtime.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
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

    public static String sendAccepted(String cmd, String clientMsgId, String requestId) {
        return json(baseMessage("sendAccepted", cmd, clientMsgId, requestId));
    }

    public static String sendCommitted(String cmd, String clientMsgId, String requestId, Map<String, ?> extras) {
        LinkedHashMap<String, Object> payload = baseMessage("sendCommitted", cmd, clientMsgId, requestId);
        if (extras != null && !extras.isEmpty()) {
            payload.putAll(extras);
        }
        return json(payload);
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

    public static String sendRejected(String cmd, String clientMsgId, String requestId, int code, String reasonCode, String message) {
        LinkedHashMap<String, Object> payload = baseMessage("sendRejected", cmd, clientMsgId, requestId);
        payload.put("code", code);
        payload.put("reasonCode", String.valueOf(reasonCode));
        payload.put("message", String.valueOf(message));
        return json(payload);
    }

    private static LinkedHashMap<String, Object> baseMessage(String type, String cmd, String clientMsgId, String requestId) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("cmd", String.valueOf(cmd));
        payload.put("clientMsgId", String.valueOf(clientMsgId));
        payload.put("requestId", String.valueOf(requestId));
        return payload;
    }

    private static String json(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return "{\"type\":\"error\",\"message\":\"encode_failed\"}";
        }
    }
}
