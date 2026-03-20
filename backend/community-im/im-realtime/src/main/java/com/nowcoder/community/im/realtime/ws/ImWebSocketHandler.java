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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;

@Component
public class ImWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ImWebSocketHandler.class);

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
            Disposable sub = imCoreClient.listAllRoomIdsForUser(verified.userId(), accessToken)
                    .onBackpressureBuffer(2048)
                    .doOnError(ex -> log.warn("[im-ws] room bootstrap failed (userId={}): {}", verified.userId(), ex.toString()))
                    .onErrorResume(ex -> Flux.empty())
                    .subscribe(roomId -> {
                        roomLocalIndex.add(roomId, conn.connectionId());
                        conn.joinRoom(roomId);
                    });
            conn.setRoomBootstrapSubscription(sub);
        } catch (Exception e) {
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

        return governanceClient.validateSendPrivateMessage(accessToken, toUserId)
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
            conn.trySendText(WsProtocol.sendError(cmdType, clientMsgId, requestId, "kafka send failed"));
            return;
        }
        try {
            future.whenComplete((ok, ex) -> {
                try {
                    if (ex != null) {
                        log.warn("[im-ws] kafka send failed (userId={}, cmd={}, clientMsgId={}, requestId={}): {}",
                                conn.userId(), cmdType, clientMsgId, requestId, ex.toString());
                        conn.trySendText(WsProtocol.sendError(cmdType, clientMsgId, requestId, "kafka send failed"));
                        return;
                    }
                    conn.trySendText(WsProtocol.sendAck(cmdType, clientMsgId, requestId));
                } catch (RuntimeException ignore) {
                }
            });
        } catch (RuntimeException e) {
            conn.trySendText(WsProtocol.sendError(cmdType, clientMsgId, requestId, "kafka send failed"));
        }
    }

    private void cleanup(WsConnection conn) {
        try {
            conn.disposeRoomBootstrapSubscription();
            connectionRegistry.unregister(conn);
            for (Long roomId : conn.joinedRoomsView()) {
                roomLocalIndex.remove(roomId, conn.connectionId());
            }
            conn.complete();
        } catch (RuntimeException ignore) {
        }
    }

    private static String newRequestId() {
        try {
            return UUID.randomUUID().toString();
        } catch (RuntimeException e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }
}
