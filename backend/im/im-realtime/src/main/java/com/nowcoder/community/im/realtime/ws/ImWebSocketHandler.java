package com.nowcoder.community.im.realtime.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.im.contracts.command.SendPrivateTextCommandV1;
import com.nowcoder.community.im.contracts.command.SendRoomTextCommandV1;
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
    private final CommandProducer commandProducer;
    private final int maxChars;
    private final int maxOutboundBacklog;

    public ImWebSocketHandler(
            ObjectMapper objectMapper,
            JwtVerifier jwtVerifier,
            ConnectionRegistry connectionRegistry,
            RoomLocalIndex roomLocalIndex,
            ImCoreClient imCoreClient,
            CommandProducer commandProducer,
            @Value("${im.ws.max-inbound-chars:10000}") int maxChars,
            @Value("${im.ws.outbound-buffer-size:256}") int maxOutboundBacklog
    ) {
        this.objectMapper = objectMapper;
        this.jwtVerifier = jwtVerifier;
        this.connectionRegistry = connectionRegistry;
        this.roomLocalIndex = roomLocalIndex;
        this.imCoreClient = imCoreClient;
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

        if (conn.userId() == null) {
            if (!"auth".equals(type)) {
                conn.trySendText(WsProtocol.authError("auth required"));
                conn.closeAsync(Duration.ofSeconds(1));
                return Mono.empty();
            }
            return handleAuth(conn, node);
        }

        return switch (type) {
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
            conn.bindUser(verified.userId());
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
            return Mono.empty();
        }
        int toUserId = node.path("toUserId").asInt(0);
        String content = node.path("content").asText("");
        String clientMsgId = node.path("clientMsgId").asText("");
        if (toUserId <= 0 || !StringUtils.hasText(clientMsgId) || !StringUtils.hasText(content)) {
            conn.trySendText(WsProtocol.error("invalid sendPrivateText"));
            return Mono.empty();
        }
        if (content.length() > maxChars) {
            conn.trySendText(WsProtocol.error("content too long"));
            return Mono.empty();
        }
        String conversationId = ConversationIdSupport.conversationId(fromUserId, toUserId);
        SendPrivateTextCommandV1 cmd = new SendPrivateTextCommandV1(
                newRequestId(),
                clientMsgId.trim(),
                fromUserId,
                toUserId,
                conversationId,
                content,
                System.currentTimeMillis()
        );
        commandProducer.sendPrivateText(cmd);
        return Mono.empty();
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
        commandProducer.sendRoomText(cmd);
        return Mono.empty();
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
