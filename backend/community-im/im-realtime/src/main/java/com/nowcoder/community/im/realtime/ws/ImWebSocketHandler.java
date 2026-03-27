package com.nowcoder.community.im.realtime.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.im.common.command.SendPrivateTextCommandV1;
import com.nowcoder.community.im.common.command.SendRoomTextCommandV1;
import com.nowcoder.community.im.realtime.client.CommunityGovernanceClient;
import com.nowcoder.community.im.realtime.client.ImCoreClient;
import com.nowcoder.community.im.realtime.kafka.CommandProducer;
import com.nowcoder.community.im.realtime.presence.ConnectionRegistry;
import com.nowcoder.community.im.realtime.presence.RoomLocalIndex;
import com.nowcoder.community.im.realtime.presence.WsConnection;
import com.nowcoder.community.im.realtime.security.JwtVerifier;
import com.nowcoder.community.im.realtime.support.ConversationIdSupport;
import com.nowcoder.community.im.realtime.trace.TraceHeaders;
import com.nowcoder.community.im.realtime.trace.TraceIdCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;

@Component
public class ImWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ImWebSocketHandler.class);
    private static final String CATEGORY_ACCESS = "access";
    private static final String CATEGORY_SECURITY = "security";
    private static final String CATEGORY_INTEGRATION = "integration";
    private static final String MDC_CATEGORY = "community.category";
    private static final String MDC_ACTION = "community.action";
    private static final String MDC_OUTCOME = "community.outcome";
    private static final String MDC_TRACE_ID = "traceId";

    private final ObjectMapper objectMapper;
    private final JwtVerifier jwtVerifier;
    private final ConnectionRegistry connectionRegistry;
    private final RoomLocalIndex roomLocalIndex;
    private final ImCoreClient imCoreClient;
    private final CommunityGovernanceClient governanceClient;
    private final CommandProducer commandProducer;
    private final int maxChars;
    private final int maxOutboundBacklog;

    public ImWebSocketHandler(
            ObjectMapper objectMapper,
            JwtVerifier jwtVerifier,
            ConnectionRegistry connectionRegistry,
            RoomLocalIndex roomLocalIndex,
            ImCoreClient imCoreClient,
            CommunityGovernanceClient governanceClient,
            CommandProducer commandProducer,
            @Value("${im.ws.max-inbound-chars:10000}") int maxChars,
            @Value("${im.ws.outbound-buffer-size:256}") int maxOutboundBacklog
    ) {
        this.objectMapper = objectMapper;
        this.jwtVerifier = jwtVerifier;
        this.connectionRegistry = connectionRegistry;
        this.roomLocalIndex = roomLocalIndex;
        this.imCoreClient = imCoreClient;
        this.governanceClient = governanceClient;
        this.commandProducer = commandProducer;
        this.maxChars = Math.min(Math.max(1, maxChars), 100_000);
        this.maxOutboundBacklog = Math.min(Math.max(1, maxOutboundBacklog), 10_000);
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        WsConnection conn = new WsConnection(session.getId(), session, maxOutboundBacklog);
        conn.bindTrace(resolveTraceId(session));

        AtomicBoolean cleaned = new AtomicBoolean(false);
        Runnable cleanupOnce = () -> {
            if (cleaned.compareAndSet(false, true)) {
                cleanup(conn);
            }
        };

        Flux<WebSocketMessage> outboundFlux = conn.outboundSink()
                .asFlux()
                .doOnNext(msg -> conn.onOutboundDelivered())
                .map(session::textMessage);

        Mono<Void> sender = session.send(outboundFlux)
                .doFinally(sig -> cleanupOnce.run());

        Mono<Void> receiver = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(text -> handleInboundText(conn, text))
                .doFinally(sig -> cleanupOnce.run())
                .then();

        return Mono.when(sender, receiver);
    }

    private Mono<Void> handleInboundText(WsConnection conn, String text) {
        if (text == null || text.isBlank()) {
            return Mono.empty();
        }
        if (text.length() > maxChars) {
            conn.trySendText(WsProtocol.error("payload too large"));
            conn.closeAsync(Duration.ofSeconds(1));
            return Mono.empty();
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(text);
        } catch (Exception e) {
            conn.trySendText(WsProtocol.error("invalid json"));
            return Mono.empty();
        }
        String type = node.path("type").asText("");
        if (!StringUtils.hasText(type)) {
            conn.trySendText(WsProtocol.error("missing type"));
            return Mono.empty();
        }

        if (conn.userId() == null && !"auth".equals(type)) {
            warnEvent(
                    CATEGORY_SECURITY,
                    "ws_auth",
                    "denied",
                    conn.traceId(),
                    null,
                    "community.reason_code", "auth_required",
                    "community.connection_id", conn.connectionId()
            );
            conn.trySendText(WsProtocol.authError("auth required"));
            conn.closeAsync(Duration.ofSeconds(1));
            return Mono.empty();
        }

        return switch (type) {
            case "auth" -> handleAuth(conn, node);
            case "sendPrivateText" -> handleSendPrivate(conn, node);
            case "sendRoomText" -> handleSendRoom(conn, node);
            case "ping" -> {
                conn.trySendText(WsProtocol.pong());
                yield Mono.empty();
            }
            default -> Mono.empty();
        };
    }

    private Mono<Void> handleAuth(WsConnection conn, JsonNode node) {
        String accessToken = node.path("accessToken").asText("");
        try {
            JwtVerifier.VerifiedJwt verified = jwtVerifier.verify(accessToken);
            Integer previous = conn.userId();
            if (previous != null) {
                if (previous != verified.userId()) {
                    warnEvent(
                            CATEGORY_SECURITY,
                            "ws_auth",
                            "denied",
                            conn.traceId(),
                            null,
                            "community.reason_code", "user_mismatch",
                            "community.connection_id", conn.connectionId(),
                            "user.id", previous
                    );
                    conn.trySendText(WsProtocol.authError("user mismatch"));
                    conn.closeAsync(Duration.ofSeconds(1));
                    return Mono.empty();
                }
                // Token refresh (do not re-bootstrap rooms, but update token for downstream calls).
                conn.bindAuth(verified.userId(), accessToken);
                conn.trySendText(WsProtocol.authOk(verified.userId()));
                return Mono.empty();
            }

            conn.bindAuth(verified.userId(), accessToken);
            connectionRegistry.register(conn);
            conn.trySendText(WsProtocol.authOk(verified.userId()));

            // Best-effort bootstrap: pull membership from im-core (paged) and build local indexes.
            Disposable sub = imCoreClient.listAllRoomIdsForUser(verified.userId(), accessToken, conn.traceId())
                    .onBackpressureBuffer(2048)
                    .doOnError(ex -> warnEvent(
                            CATEGORY_INTEGRATION,
                            "ws_room_bootstrap",
                            "degraded",
                            conn.traceId(),
                            null,
                            "community.reason_code", "bootstrap_failed",
                            "community.connection_id", conn.connectionId(),
                            "user.id", verified.userId(),
                            "community.error_class", errorClass(ex),
                            "community.error_message", errorMessage(ex)
                    ))
                    .onErrorResume(ex -> Flux.empty())
                    .subscribe(roomId -> {
                        roomLocalIndex.add(roomId, conn.connectionId());
                        conn.joinRoom(roomId);
                    });
            conn.setRoomBootstrapSubscription(sub);
        } catch (Exception e) {
            warnEvent(
                CATEGORY_SECURITY,
                "ws_auth",
                "denied",
                conn.traceId(),
                null,
                "community.reason_code", "invalid_token",
                "community.connection_id", conn.connectionId(),
                    "community.error_class", errorClass(e),
                    "community.error_message", errorMessage(e)
            );
            conn.trySendText(WsProtocol.authError("invalid token"));
            conn.closeAsync(Duration.ofSeconds(1));
        }
        return Mono.empty();
    }

    private Mono<Void> handleSendPrivate(WsConnection conn, JsonNode node) {
        Integer fromUserId = conn.userId();
        if (fromUserId == null) {
            conn.trySendText(WsProtocol.authError("auth required"));
            conn.closeAsync(Duration.ofSeconds(1));
            return Mono.empty();
        }
        int toUserId = node.path("toUserId").asInt(0);
        String content = node.path("content").asText("");
        String clientMsgId = String.valueOf(node.path("clientMsgId").asText("")).trim();
        String requestId = newRequestId();
        if (toUserId <= 0) {
            conn.trySendText(WsProtocol.sendError("sendPrivateText", clientMsgId, requestId, 400, "toUserId 非法", ""));
            return Mono.empty();
        }
        if (!StringUtils.hasText(clientMsgId)) {
            conn.trySendText(WsProtocol.error("clientMsgId required"));
            return Mono.empty();
        }
        if (!StringUtils.hasText(content)) {
            conn.trySendText(WsProtocol.sendError("sendPrivateText", clientMsgId, requestId, 400, "content required", ""));
            return Mono.empty();
        }
        if (content.length() > maxChars) {
            conn.trySendText(WsProtocol.sendError("sendPrivateText", clientMsgId, requestId, 400, "content too long", ""));
            return Mono.empty();
        }

        String accessToken = conn.accessToken();
        if (!StringUtils.hasText(accessToken)) {
            conn.trySendText(WsProtocol.sendError("sendPrivateText", clientMsgId, requestId, 401, "未登录或登录已失效", ""));
            conn.closeAsync(Duration.ofSeconds(1));
            return Mono.empty();
        }

        return governanceClient.validateSendPrivateMessage(accessToken, toUserId, conn.traceId())
                .flatMap(decision -> {
                    if (decision == null || !decision.allowed()) {
                        int code = decision == null ? 503 : decision.code();
                        String msg = decision == null ? "治理校验服务不可用，请稍后重试" : decision.message();
                        String traceId = decision == null ? "" : decision.traceId();
                        conn.trySendText(WsProtocol.sendError("sendPrivateText", clientMsgId, requestId, code, msg, traceId));
                        if (code == 401) {
                            conn.trySendText(WsProtocol.authError("invalid token"));
                            conn.closeAsync(Duration.ofSeconds(1));
                        }
                        return Mono.empty();
                    }

                    String conversationId = ConversationIdSupport.conversationId(fromUserId, toUserId);
                    SendPrivateTextCommandV1 cmd = new SendPrivateTextCommandV1(
                            requestId,
                            clientMsgId,
                            fromUserId,
                            toUserId,
                            conversationId,
                            content,
                            System.currentTimeMillis()
                    );
                    CompletableFuture<?> f;
                    try {
                        f = commandProducer.sendPrivateText(cmd);
                    } catch (RuntimeException e) {
                        f = CompletableFuture.failedFuture(e);
                    }
                    sendWithAck(conn, "sendPrivateText", cmd.clientMsgId(), cmd.requestId(), f);
                    return Mono.empty();
                });
    }

    private Mono<Void> handleSendRoom(WsConnection conn, JsonNode node) {
        Integer fromUserId = conn.userId();
        if (fromUserId == null) {
            return Mono.empty();
        }
        long roomId = node.path("roomId").asLong(0L);
        String content = node.path("content").asText("");
        String clientMsgId = node.path("clientMsgId").asText("");
        if (roomId <= 0L || !StringUtils.hasText(clientMsgId) || !StringUtils.hasText(content)) {
            conn.trySendText(WsProtocol.error("invalid sendRoomText"));
            return Mono.empty();
        }
        if (content.length() > maxChars) {
            conn.trySendText(WsProtocol.error("content too long"));
            return Mono.empty();
        }
        SendRoomTextCommandV1 cmd = new SendRoomTextCommandV1(
                newRequestId(),
                clientMsgId.trim(),
                fromUserId,
                roomId,
                content,
                System.currentTimeMillis()
        );
        CompletableFuture<?> f;
        try {
            f = commandProducer.sendRoomText(cmd);
        } catch (RuntimeException e) {
            f = CompletableFuture.failedFuture(e);
        }
        sendWithAck(conn, "sendRoomText", cmd.clientMsgId(), cmd.requestId(), f);
        return Mono.empty();
    }

    private void sendWithAck(
            WsConnection conn,
            String cmdType,
            String clientMsgId,
            String requestId,
            CompletableFuture<?> future
    ) {
        if (conn == null) {
            return;
        }
        if (future == null) {
            warnEvent(
                    CATEGORY_INTEGRATION,
                    "ws_command_enqueue",
                    "failure",
                    conn.traceId(),
                    null,
                    "community.reason_code", "kafka_send_failed",
                    "community.connection_id", conn.connectionId(),
                    "user.id", conn.userId(),
                    "community.command", cmdType,
                    "community.client_msg_id", clientMsgId,
                    "community.request_id", requestId
            );
            conn.trySendText(WsProtocol.sendError(cmdType, clientMsgId, requestId, "kafka send failed"));
            return;
        }
        try {
            future.whenComplete((ok, ex) -> {
                try {
                    if (ex != null) {
                        warnEvent(
                                CATEGORY_INTEGRATION,
                                "ws_command_enqueue",
                                "failure",
                                conn.traceId(),
                                null,
                                "community.reason_code", "kafka_send_failed",
                                "community.connection_id", conn.connectionId(),
                                "user.id", conn.userId(),
                                "community.command", cmdType,
                                "community.client_msg_id", clientMsgId,
                                "community.request_id", requestId,
                                "community.error_class", errorClass(ex),
                                "community.error_message", errorMessage(ex)
                        );
                        conn.trySendText(WsProtocol.sendError(cmdType, clientMsgId, requestId, "kafka send failed"));
                        return;
                    }
                    conn.trySendText(WsProtocol.sendAck(cmdType, clientMsgId, requestId));
                } catch (RuntimeException ignore) {
                }
            });
        } catch (RuntimeException e) {
            warnEvent(
                    CATEGORY_INTEGRATION,
                    "ws_command_enqueue",
                    "failure",
                    conn.traceId(),
                    null,
                    "community.reason_code", "kafka_send_failed",
                    "community.connection_id", conn.connectionId(),
                    "user.id", conn.userId(),
                    "community.command", cmdType,
                    "community.client_msg_id", clientMsgId,
                    "community.request_id", requestId,
                    "community.error_class", errorClass(e),
                    "community.error_message", errorMessage(e)
            );
            conn.trySendText(WsProtocol.sendError(cmdType, clientMsgId, requestId, "kafka send failed"));
        }
    }

    private void cleanup(WsConnection conn) {
        if (conn == null) {
            return;
        }
        int joinedRoomCount = conn.joinedRoomsView().size();
        int outboundBacklog = conn.outboundBacklog();
        try {
            conn.disposeRoomBootstrapSubscription();
            connectionRegistry.unregister(conn);
            for (Long roomId : conn.joinedRoomsView()) {
                roomLocalIndex.remove(roomId, conn.connectionId());
            }
            conn.complete();
        } catch (RuntimeException ignore) {
        } finally {
            infoEvent(
                    CATEGORY_ACCESS,
                    "ws_disconnect",
                    "success",
                    conn.traceId(),
                    "community.connection_id", conn.connectionId(),
                    "user.id", conn.userId(),
                    "community.joined_room_count", joinedRoomCount,
                    "community.outbound_backlog", outboundBacklog
            );
        }
    }

    private static String newRequestId() {
        try {
            return UUID.randomUUID().toString();
        } catch (RuntimeException e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    private String resolveTraceId(WebSocketSession session) {
        if (session == null || session.getHandshakeInfo() == null || session.getHandshakeInfo().getHeaders() == null) {
            return TraceIdCodec.generateTraceId();
        }
        String traceIdHeader = session.getHandshakeInfo().getHeaders().getFirst(TraceHeaders.HEADER_TRACE_ID);
        String traceparentHeader = session.getHandshakeInfo().getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT);
        return TraceIdCodec.resolveTraceId(traceIdHeader, traceparentHeader);
    }

    private void infoEvent(String category, String action, String outcome, String traceId, Object... keyValues) {
        logEvent(category, action, outcome, traceId, false, null, keyValues);
    }

    private void warnEvent(String category, String action, String outcome, String traceId, Throwable throwable, Object... keyValues) {
        logEvent(category, action, outcome, traceId, true, throwable, keyValues);
    }

    private void logEvent(String category, String action, String outcome, String traceId, boolean warn, Throwable throwable, Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("IM realtime event keyValues must contain key/value pairs");
        }
        String previousCategory = MDC.get(MDC_CATEGORY);
        String previousAction = MDC.get(MDC_ACTION);
        String previousOutcome = MDC.get(MDC_OUTCOME);
        String previousTraceId = MDC.get(MDC_TRACE_ID);
        String resolvedCategory = StringUtils.hasText(category) ? category.trim() : CATEGORY_INTEGRATION;
        MDC.put(MDC_CATEGORY, resolvedCategory);
        MDC.put(MDC_ACTION, action);
        MDC.put(MDC_OUTCOME, outcome);
        if (StringUtils.hasText(traceId)) {
            MDC.put(MDC_TRACE_ID, traceId);
        } else {
            MDC.remove(MDC_TRACE_ID);
        }
        try {
            String message = buildMessage(resolvedCategory, action, outcome, keyValues);
            if (warn) {
                if (throwable == null) {
                    log.warn(message);
                } else {
                    log.warn(message, throwable);
                }
                return;
            }
            log.info(message);
        } finally {
            restore(MDC_CATEGORY, previousCategory);
            restore(MDC_ACTION, previousAction);
            restore(MDC_OUTCOME, previousOutcome);
            restore(MDC_TRACE_ID, previousTraceId);
        }
    }

    private String buildMessage(String category, String action, String outcome, Object... keyValues) {
        StringBuilder message = new StringBuilder(192);
        appendToken(message, MDC_CATEGORY, category);
        appendToken(message, MDC_ACTION, action);
        appendToken(message, MDC_OUTCOME, outcome);
        for (int i = 0; i < keyValues.length; i += 2) {
            appendToken(message, String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return message.toString();
    }

    private void appendToken(StringBuilder message, String key, Object value) {
        if (message.length() > 0) {
            message.append(' ');
        }
        message.append(key).append('=').append(encodeTokenValue(value));
    }

    private String encodeTokenValue(Object value) {
        if (value == null) {
            return "-";
        }
        String raw = String.valueOf(value);
        if (raw.isEmpty()) {
            return "-";
        }
        StringBuilder encoded = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (Character.isWhitespace(ch) || Character.isISOControl(ch) || ch == '=' || ch == '%') {
                encoded.append('%');
                String hex = Integer.toHexString(ch).toUpperCase(Locale.ROOT);
                if (hex.length() == 1) {
                    encoded.append('0');
                }
                encoded.append(hex);
            } else {
                encoded.append(ch);
            }
        }
        return encoded.toString();
    }

    private void restore(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, previousValue);
    }

    private String errorClass(Throwable throwable) {
        return throwable == null ? null : throwable.getClass().getName();
    }

    private String errorMessage(Throwable throwable) {
        return throwable == null ? null : throwable.getMessage();
    }
}
