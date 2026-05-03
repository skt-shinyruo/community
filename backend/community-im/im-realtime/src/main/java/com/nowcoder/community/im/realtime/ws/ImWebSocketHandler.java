package com.nowcoder.community.im.realtime.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.trace.TraceIdCodec;
import com.nowcoder.community.im.common.ws.ConnectFrame;
import com.nowcoder.community.im.common.ws.ConnectedFrame;
import com.nowcoder.community.im.common.ws.PingFrame;
import com.nowcoder.community.im.common.ws.PongFrame;
import com.nowcoder.community.im.common.ws.RejectFrame;
import com.nowcoder.community.im.common.ws.SendPrivateTextFrame;
import com.nowcoder.community.im.common.ws.SendRoomTextFrame;
import com.nowcoder.community.im.realtime.presence.ConnectionRegistry;
import com.nowcoder.community.im.realtime.presence.RoomLocalIndex;
import com.nowcoder.community.im.realtime.presence.WsConnection;
import com.nowcoder.community.im.realtime.projection.MembershipProjectionService;
import com.nowcoder.community.im.realtime.projection.PolicyDecision;
import com.nowcoder.community.im.realtime.projection.PolicyProjectionService;
import com.nowcoder.community.im.realtime.projection.ProjectionSyncCoordinator;
import com.nowcoder.community.im.realtime.service.MessageCommandIngressService;
import com.nowcoder.community.im.realtime.session.ImSessionProperties;
import com.nowcoder.community.im.realtime.session.SessionTicketCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ImWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ImWebSocketHandler.class);
    private static final String CATEGORY_ACCESS = "access";
    private static final String CATEGORY_SECURITY = "security";
    private static final String MDC_CATEGORY = "community.category";
    private static final String MDC_ACTION = "community.action";
    private static final String MDC_OUTCOME = "community.outcome";
    private static final String MDC_TRACE_ID = "traceId";

    private final ImFrameCodec frameCodec;
    private final SessionTicketCodec sessionTicketCodec;
    private final ImSessionProperties sessionProperties;
    private final ProjectionSyncCoordinator projectionSyncCoordinator;
    private final MembershipProjectionService membershipProjectionService;
    private final PolicyProjectionService policyProjectionService;
    private final MessageCommandIngressService commandIngressService;
    private final ConnectionRegistry connectionRegistry;
    private final RoomLocalIndex roomLocalIndex;
    private final int maxChars;
    private final int maxOutboundBacklog;

    public ImWebSocketHandler(
            ImFrameCodec frameCodec,
            SessionTicketCodec sessionTicketCodec,
            ImSessionProperties sessionProperties,
            ProjectionSyncCoordinator projectionSyncCoordinator,
            MembershipProjectionService membershipProjectionService,
            PolicyProjectionService policyProjectionService,
            MessageCommandIngressService commandIngressService,
            ConnectionRegistry connectionRegistry,
            RoomLocalIndex roomLocalIndex,
            @Value("${im.ws.max-inbound-chars:10000}") int maxChars,
            @Value("${im.ws.outbound-buffer-size:256}") int maxOutboundBacklog
    ) {
        this.frameCodec = frameCodec;
        this.sessionTicketCodec = sessionTicketCodec;
        this.sessionProperties = sessionProperties;
        this.projectionSyncCoordinator = projectionSyncCoordinator;
        this.membershipProjectionService = membershipProjectionService;
        this.policyProjectionService = policyProjectionService;
        this.commandIngressService = commandIngressService;
        this.connectionRegistry = connectionRegistry;
        this.roomLocalIndex = roomLocalIndex;
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

        Mono<Void> sender = session.send(outboundFlux).doFinally(signalType -> cleanupOnce.run());
        Mono<Void> receiver = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(text -> handleInboundText(conn, text))
                .doFinally(signalType -> cleanupOnce.run())
                .then();

        return Mono.when(sender, receiver);
    }

    private Mono<Void> handleInboundText(WsConnection conn, String text) {
        if (!StringUtils.hasText(text)) {
            return Mono.empty();
        }
        if (text.length() > maxChars) {
            rejectAndClose(conn, "protocol", "", "", 400, "payload_too_large", "payload too large");
            return Mono.empty();
        }

        JsonNode node;
        try {
            node = frameCodec.readTree(text);
        } catch (RuntimeException e) {
            sendReject(conn, "protocol", "", "", 400, "invalid_json", "invalid json");
            return Mono.empty();
        }

        String type = node.path("type").asText("");
        if (!StringUtils.hasText(type)) {
            sendReject(conn, "protocol", "", "", 400, "missing_type", "missing type");
            return Mono.empty();
        }

        if (conn.userId() == null && !"connect".equals(type)) {
            warnEvent(
                    CATEGORY_SECURITY,
                    "ws_connect",
                    "denied",
                    conn.traceId(),
                    "community.reason_code", "connect_required",
                    "community.connection_id", conn.connectionId()
            );
            rejectAndClose(conn, type, "", "", 401, "connect_required", "connect required");
            return Mono.empty();
        }

        return switch (type) {
            case "connect" -> handleConnect(conn, node);
            case "sendPrivateText" -> handleSendPrivate(conn, node);
            case "sendRoomText" -> handleSendRoom(conn, node);
            case "ping" -> handlePing(conn, node);
            default -> {
                sendReject(conn, type, "", "", 400, "unsupported_type", "unsupported type");
                yield Mono.empty();
            }
        };
    }

    private Mono<Void> handleConnect(WsConnection conn, JsonNode node) {
        try {
            projectionSyncCoordinator.requireReady();
            ConnectFrame frame = frameCodec.read(node, ConnectFrame.class);
            SessionTicketCodec.TicketClaims ticket = sessionTicketCodec.decode(frame.ticket());

            if (!StringUtils.hasText(ticket.workerId())
                    || !ticket.workerId().equals(sessionProperties.getWorkerId())) {
                rejectAndClose(conn, "connect", "", "", 403, "wrong_worker", "ticket is bound to another worker");
                return Mono.empty();
            }

            if (conn.userId() != null) {
                conn.trySendText(frameCodec.write(new ConnectedFrame("connected", conn.sessionId())));
                return Mono.empty();
            }

            conn.bindSession(ticket.sessionId(), ticket.userId(), ticket.workerId());
            membershipProjectionService.bindExistingRooms(conn, roomLocalIndex);
            connectionRegistry.register(conn);
            conn.trySendText(frameCodec.write(new ConnectedFrame("connected", ticket.sessionId())));
            infoEvent(
                    CATEGORY_ACCESS,
                    "ws_connect",
                    "success",
                    conn.traceId(),
                    "community.connection_id", conn.connectionId(),
                    "user.id", conn.userId(),
                    "community.session_id", conn.sessionId(),
                    "community.worker_id", conn.workerId()
            );
        } catch (ResponseStatusException e) {
            rejectAndClose(conn, "connect", "", "", e.getStatusCode().value(), "projection_not_ready", e.getReason());
        } catch (RuntimeException e) {
            warnEvent(
                    CATEGORY_SECURITY,
                    "ws_connect",
                    "denied",
                    conn.traceId(),
                    "community.reason_code", "invalid_ticket",
                    "community.connection_id", conn.connectionId(),
                    "community.error_class", errorClass(e),
                    "community.error_message", errorMessage(e)
            );
            rejectAndClose(conn, "connect", "", "", 401, "invalid_ticket", "invalid ticket");
        }
        return Mono.empty();
    }

    private Mono<Void> handleSendPrivate(WsConnection conn, JsonNode node) {
        SendPrivateTextFrame frame;
        try {
            projectionSyncCoordinator.requireReady();
            frame = frameCodec.read(node, SendPrivateTextFrame.class);
        } catch (ResponseStatusException e) {
            sendReject(conn, "sendPrivateText", "", "", e.getStatusCode().value(), "projection_not_ready", e.getReason());
            return Mono.empty();
        } catch (RuntimeException e) {
            sendReject(conn, "sendPrivateText", "", "", 400, "invalid_frame", "invalid sendPrivateText");
            return Mono.empty();
        }

        String clientMsgId = frame.clientMsgId() == null ? "" : frame.clientMsgId().trim();
        if (!StringUtils.hasText(clientMsgId) || frame.toUserId() == null || !StringUtils.hasText(frame.content())) {
            sendReject(conn, "sendPrivateText", clientMsgId, "", 400, "invalid_frame", "invalid sendPrivateText");
            return Mono.empty();
        }
        if (frame.content().length() > maxChars) {
            sendReject(conn, "sendPrivateText", clientMsgId, "", 400, "content_too_long", "content too long");
            return Mono.empty();
        }

        PolicyDecision decision = policyProjectionService.canSendPrivate(conn.userId(), frame.toUserId());
        if (!decision.allowed()) {
            sendReject(
                    conn,
                    "sendPrivateText",
                    clientMsgId,
                    UUID.randomUUID().toString(),
                    decision.code(),
                    decision.reasonCode(),
                    decision.message()
            );
            return Mono.empty();
        }
        return commandIngressService.sendPrivate(conn, frame.toUserId(), clientMsgId, frame.content());
    }

    private Mono<Void> handleSendRoom(WsConnection conn, JsonNode node) {
        SendRoomTextFrame frame;
        try {
            projectionSyncCoordinator.requireReady();
            frame = frameCodec.read(node, SendRoomTextFrame.class);
        } catch (ResponseStatusException e) {
            sendReject(conn, "sendRoomText", "", "", e.getStatusCode().value(), "projection_not_ready", e.getReason());
            return Mono.empty();
        } catch (RuntimeException e) {
            sendReject(conn, "sendRoomText", "", "", 400, "invalid_frame", "invalid sendRoomText");
            return Mono.empty();
        }

        String clientMsgId = frame.clientMsgId() == null ? "" : frame.clientMsgId().trim();
        if (!StringUtils.hasText(clientMsgId) || frame.roomId() == null || !StringUtils.hasText(frame.content())) {
            sendReject(conn, "sendRoomText", clientMsgId, "", 400, "invalid_frame", "invalid sendRoomText");
            return Mono.empty();
        }
        if (!membershipProjectionService.isMember(frame.roomId(), conn.userId())) {
            sendReject(conn, "sendRoomText", clientMsgId, UUID.randomUUID().toString(), 403, "not_room_member", "not a room member");
            return Mono.empty();
        }
        if (frame.content().length() > maxChars) {
            sendReject(conn, "sendRoomText", clientMsgId, "", 400, "content_too_long", "content too long");
            return Mono.empty();
        }
        return commandIngressService.sendRoom(conn, frame.roomId(), clientMsgId, frame.content());
    }

    private Mono<Void> handlePing(WsConnection conn, JsonNode node) {
        long sentAtEpochMillis;
        try {
            PingFrame frame = frameCodec.read(node, PingFrame.class);
            sentAtEpochMillis = frame.sentAtEpochMillis();
        } catch (RuntimeException e) {
            sentAtEpochMillis = System.currentTimeMillis();
        }
        conn.trySendText(frameCodec.write(new PongFrame("pong", sentAtEpochMillis)));
        return Mono.empty();
    }

    private void cleanup(WsConnection conn) {
        if (conn == null) {
            return;
        }
        int joinedRoomCount = conn.joinedRoomsView().size();
        int outboundBacklog = conn.outboundBacklog();
        try {
            connectionRegistry.unregister(conn);
            for (UUID roomId : conn.joinedRoomsView()) {
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

    private void rejectAndClose(
            WsConnection conn,
            String cmd,
            String clientMsgId,
            String requestId,
            int code,
            String reasonCode,
            String message
    ) {
        sendReject(conn, cmd, clientMsgId, requestId, code, reasonCode, message);
        conn.closeAsync(Duration.ofSeconds(1));
    }

    private void sendReject(
            WsConnection conn,
            String cmd,
            String clientMsgId,
            String requestId,
            int code,
            String reasonCode,
            String message
    ) {
        conn.trySendText(frameCodec.write(new RejectFrame(
                "reject",
                cmd == null ? "" : cmd,
                clientMsgId == null ? "" : clientMsgId,
                requestId == null ? "" : requestId,
                code,
                reasonCode == null ? "" : reasonCode,
                message == null ? "" : message
        )));
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
        logEvent(category, action, outcome, traceId, false, keyValues);
    }

    private void warnEvent(String category, String action, String outcome, String traceId, Object... keyValues) {
        logEvent(category, action, outcome, traceId, true, keyValues);
    }

    private void logEvent(String category, String action, String outcome, String traceId, boolean warn, Object... keyValues) {
        String previousCategory = MDC.get(MDC_CATEGORY);
        String previousAction = MDC.get(MDC_ACTION);
        String previousOutcome = MDC.get(MDC_OUTCOME);
        String previousTraceId = MDC.get(MDC_TRACE_ID);
        MDC.put(MDC_CATEGORY, category);
        MDC.put(MDC_ACTION, action);
        MDC.put(MDC_OUTCOME, outcome);
        if (StringUtils.hasText(traceId)) {
            MDC.put(MDC_TRACE_ID, traceId);
        } else {
            MDC.remove(MDC_TRACE_ID);
        }
        try {
            String message = buildMessage(category, action, outcome, keyValues);
            if (warn) {
                log.warn(message);
            } else {
                log.info(message);
            }
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
        } else {
            MDC.put(key, previousValue);
        }
    }

    private String errorClass(Throwable throwable) {
        return throwable == null ? null : throwable.getClass().getName();
    }

    private String errorMessage(Throwable throwable) {
        return throwable == null ? null : throwable.getMessage();
    }
}
