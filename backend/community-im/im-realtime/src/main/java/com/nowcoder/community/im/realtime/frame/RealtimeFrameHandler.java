package com.nowcoder.community.im.realtime.frame;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.im.common.ImUnsupportedSchemaVersionException;
import com.nowcoder.community.im.common.ws.AckFrame;
import com.nowcoder.community.im.common.ws.ConnectFrame;
import com.nowcoder.community.im.common.ws.ConnectedFrame;
import com.nowcoder.community.im.common.ws.PingFrame;
import com.nowcoder.community.im.common.ws.PongFrame;
import com.nowcoder.community.im.common.ws.RejectFrame;
import com.nowcoder.community.im.common.ws.SendPrivateTextFrame;
import com.nowcoder.community.im.common.ws.SendRoomTextFrame;
import com.nowcoder.community.im.realtime.projection.MembershipProjectionService;
import com.nowcoder.community.im.realtime.projection.PolicyDecision;
import com.nowcoder.community.im.realtime.projection.PolicyProjectionService;
import com.nowcoder.community.im.realtime.projection.ProjectionSyncCoordinator;
import com.nowcoder.community.im.realtime.service.CommandIngressResult;
import com.nowcoder.community.im.realtime.service.MessageCommandIngressService;
import com.nowcoder.community.im.realtime.service.ConnectionLifecycleService;
import com.nowcoder.community.im.realtime.session.ConnectionSession;
import com.nowcoder.community.im.realtime.session.ImSessionProperties;
import com.nowcoder.community.im.realtime.session.RealtimeEventLog;
import com.nowcoder.community.im.ticket.SessionTicketCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Transport-free realtime frame module: parses and validates inbound text frames,
 * runs the connect/auth gate, policy and membership checks and command ingress, and
 * writes answer frames through the connection's {@link com.nowcoder.community.im.realtime.session.ConnectionOutput}.
 * It never sees the WebSocket session or Reactor sink; the transport handler only
 * feeds it inbound text and owns socket plumbing.
 */
@Component
public class RealtimeFrameHandler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeFrameHandler.class);

    private static final Map<String, ImFrameCodec.FieldType> CONNECT_FRAME_FIELDS = Map.of(
            "ticket", ImFrameCodec.FieldType.TEXT
    );
    private static final Map<String, ImFrameCodec.FieldType> SEND_PRIVATE_FRAME_FIELDS = Map.of(
            "clientMsgId", ImFrameCodec.FieldType.TEXT,
            "toUserId", ImFrameCodec.FieldType.TEXT,
            "content", ImFrameCodec.FieldType.TEXT
    );
    private static final Map<String, ImFrameCodec.FieldType> SEND_ROOM_FRAME_FIELDS = Map.of(
            "clientMsgId", ImFrameCodec.FieldType.TEXT,
            "roomId", ImFrameCodec.FieldType.TEXT,
            "content", ImFrameCodec.FieldType.TEXT
    );
    private static final Map<String, ImFrameCodec.FieldType> PING_FRAME_FIELDS = Map.of(
            "sentAtEpochMillis", ImFrameCodec.FieldType.LONG
    );

    private final ImFrameCodec frameCodec;
    private final SessionTicketCodec sessionTicketCodec;
    private final ImSessionProperties sessionProperties;
    private final ProjectionSyncCoordinator projectionSyncCoordinator;
    private final MembershipProjectionService membershipProjectionService;
    private final PolicyProjectionService policyProjectionService;
    private final MessageCommandIngressService commandIngressService;
    private final ConnectionLifecycleService connectionLifecycleService;
    private final int maxChars;

    public RealtimeFrameHandler(
            ImFrameCodec frameCodec,
            SessionTicketCodec sessionTicketCodec,
            ImSessionProperties sessionProperties,
            ProjectionSyncCoordinator projectionSyncCoordinator,
            MembershipProjectionService membershipProjectionService,
            PolicyProjectionService policyProjectionService,
            MessageCommandIngressService commandIngressService,
            ConnectionLifecycleService connectionLifecycleService,
            @Value("${im.ws.max-inbound-chars:10000}") int maxChars
    ) {
        this.frameCodec = frameCodec;
        this.sessionTicketCodec = sessionTicketCodec;
        this.sessionProperties = sessionProperties;
        this.projectionSyncCoordinator = projectionSyncCoordinator;
        this.membershipProjectionService = membershipProjectionService;
        this.policyProjectionService = policyProjectionService;
        this.commandIngressService = commandIngressService;
        this.connectionLifecycleService = connectionLifecycleService;
        this.maxChars = Math.min(Math.max(1, maxChars), 100_000);
    }

    public Mono<Void> handleInboundText(ConnectionSession conn, String text) {
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

        try {
            frameCodec.requireSupportedSchemaVersion(node);
        } catch (ImUnsupportedSchemaVersionException e) {
            rejectAndClose(conn, "protocol", "", "", 400, "unsupported_schema_version", e.getMessage());
            return Mono.empty();
        }

        String type = node.path("type").asText("");
        if (!StringUtils.hasText(type)) {
            sendReject(conn, "protocol", "", "", 400, "missing_type", "missing type");
            return Mono.empty();
        }

        if (conn.userId() == null && !"connect".equals(type)) {
            RealtimeEventLog.warn(
                    log,
                    RealtimeEventLog.CATEGORY_SECURITY,
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

    private Mono<Void> handleConnect(ConnectionSession conn, JsonNode node) {
        ConnectFrame frame;
        try {
            frame = frameCodec.read(node, ConnectFrame.class, CONNECT_FRAME_FIELDS);
        } catch (ImUnsupportedSchemaVersionException e) {
            rejectAndClose(conn, "protocol", "", "", 400, "unsupported_schema_version", e.getMessage());
            return Mono.empty();
        } catch (RuntimeException e) {
            rejectAndClose(conn, "connect", "", "", 400, "invalid_frame", "invalid connect");
            return Mono.empty();
        }

        try {
            projectionSyncCoordinator.requireReady();
            SessionTicketCodec.TicketClaims ticket = sessionTicketCodec.decode(frame.ticket());

            if (!StringUtils.hasText(ticket.workerId())
                    || !ticket.workerId().equals(sessionProperties.getWorkerId())) {
                rejectAndClose(conn, "connect", "", "", 403, "wrong_worker", "ticket is bound to another worker");
                return Mono.empty();
            }

            if (conn.userId() != null) {
                conn.output().trySendText(frameCodec.write(new ConnectedFrame("connected", conn.sessionId())));
                return Mono.empty();
            }

            connectionLifecycleService.connect(conn, ticket.sessionId(), ticket.userId(), ticket.workerId());
            conn.output().trySendText(frameCodec.write(new ConnectedFrame("connected", ticket.sessionId())));
            RealtimeEventLog.info(
                    log,
                    RealtimeEventLog.CATEGORY_ACCESS,
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
            RealtimeEventLog.warn(
                    log,
                    RealtimeEventLog.CATEGORY_SECURITY,
                    "ws_connect",
                    "denied",
                    conn.traceId(),
                    "community.reason_code", "invalid_ticket",
                    "community.connection_id", conn.connectionId(),
                    "community.error_class", errorClass(e)
            );
            rejectAndClose(conn, "connect", "", "", 401, "invalid_ticket", "invalid ticket");
        }
        return Mono.empty();
    }

    private Mono<Void> handleSendPrivate(ConnectionSession conn, JsonNode node) {
        SendPrivateTextFrame frame;
        try {
            frame = frameCodec.read(node, SendPrivateTextFrame.class, SEND_PRIVATE_FRAME_FIELDS);
        } catch (ImUnsupportedSchemaVersionException e) {
            sendReject(conn, "protocol", "", "", 400, "unsupported_schema_version", e.getMessage());
            return Mono.empty();
        } catch (RuntimeException e) {
            sendReject(conn, "sendPrivateText", clientMsgIdForReject(node), "", 400, "invalid_frame", "invalid sendPrivateText");
            return Mono.empty();
        }

        try {
            projectionSyncCoordinator.requireReady();
        } catch (ResponseStatusException e) {
            sendReject(conn, "sendPrivateText", "", "", e.getStatusCode().value(), "projection_not_ready", e.getReason());
            return Mono.empty();
        }

        String clientMsgId = frame.clientMsgId().trim();
        if (!StringUtils.hasText(clientMsgId) || !StringUtils.hasText(frame.content())) {
            sendReject(conn, "sendPrivateText", clientMsgId, "", 400, "invalid_frame", "invalid sendPrivateText");
            return Mono.empty();
        }
        if (frame.content().length() > maxChars) {
            sendReject(conn, "sendPrivateText", clientMsgId, "", 400, "content_too_long", "content too long");
            return Mono.empty();
        }

        PolicyDecision decision = policyProjectionService.canSendPrivateMessage(conn.userId(), frame.toUserId());
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
        return commandIngressService.sendPrivate(conn.userId(), frame.toUserId(), clientMsgId, frame.content())
                .flatMap(result -> sendIngressResult(conn, result));
    }

    private Mono<Void> handleSendRoom(ConnectionSession conn, JsonNode node) {
        SendRoomTextFrame frame;
        try {
            frame = frameCodec.read(node, SendRoomTextFrame.class, SEND_ROOM_FRAME_FIELDS);
        } catch (ImUnsupportedSchemaVersionException e) {
            sendReject(conn, "protocol", "", "", 400, "unsupported_schema_version", e.getMessage());
            return Mono.empty();
        } catch (RuntimeException e) {
            sendReject(conn, "sendRoomText", clientMsgIdForReject(node), "", 400, "invalid_frame", "invalid sendRoomText");
            return Mono.empty();
        }

        try {
            projectionSyncCoordinator.requireReady();
        } catch (ResponseStatusException e) {
            sendReject(conn, "sendRoomText", "", "", e.getStatusCode().value(), "projection_not_ready", e.getReason());
            return Mono.empty();
        }

        String clientMsgId = frame.clientMsgId().trim();
        if (!StringUtils.hasText(clientMsgId) || !StringUtils.hasText(frame.content())) {
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
        return commandIngressService.sendRoom(conn.userId(), frame.roomId(), clientMsgId, frame.content())
                .flatMap(result -> sendIngressResult(conn, result));
    }

    private Mono<Void> handlePing(ConnectionSession conn, JsonNode node) {
        PingFrame frame;
        try {
            frame = frameCodec.read(node, PingFrame.class, PING_FRAME_FIELDS);
        } catch (ImUnsupportedSchemaVersionException e) {
            sendReject(conn, "protocol", "", "", 400, "unsupported_schema_version", e.getMessage());
            return Mono.empty();
        } catch (RuntimeException e) {
            sendReject(conn, "ping", "", "", 400, "invalid_frame", "invalid ping");
            return Mono.empty();
        }
        conn.output().trySendText(frameCodec.write(new PongFrame("pong", frame.sentAtEpochMillis())));
        return Mono.empty();
    }

    /**
     * Echoes the sender's clientMsgId in rejects only when it arrived as a
     * well-formed text field; a malformed value is never coerced into the
     * correlation id the sender matches on.
     */
    private static String clientMsgIdForReject(JsonNode node) {
        JsonNode value = node == null ? null : node.get("clientMsgId");
        if (value == null || !value.isTextual()) {
            return "";
        }
        return value.asText().trim();
    }

    private void rejectAndClose(
            ConnectionSession conn,
            String cmd,
            String clientMsgId,
            String requestId,
            int code,
            String reasonCode,
            String message
    ) {
        sendReject(conn, cmd, clientMsgId, requestId, code, reasonCode, message);
        conn.output().closeAsync(Duration.ofSeconds(1));
    }

    private Mono<Void> sendIngressResult(ConnectionSession conn, CommandIngressResult result) {
        if (result.acked()) {
            conn.output().trySendText(frameCodec.write(new AckFrame("ack", result.cmd(), result.clientMsgId(), result.requestId())));
        } else {
            sendReject(conn, result.cmd(), result.clientMsgId(), result.requestId(), result.code(), result.reasonCode(), result.message());
        }
        return Mono.empty();
    }

    private void sendReject(
            ConnectionSession conn,
            String cmd,
            String clientMsgId,
            String requestId,
            int code,
            String reasonCode,
            String message
    ) {
        conn.output().trySendText(frameCodec.write(new RejectFrame(
                "reject",
                cmd == null ? "" : cmd,
                clientMsgId == null ? "" : clientMsgId,
                requestId == null ? "" : requestId,
                code,
                reasonCode == null ? "" : reasonCode,
                message == null ? "" : message
        )));
    }

    private String errorClass(Throwable throwable) {
        return throwable == null ? null : throwable.getClass().getName();
    }
}
