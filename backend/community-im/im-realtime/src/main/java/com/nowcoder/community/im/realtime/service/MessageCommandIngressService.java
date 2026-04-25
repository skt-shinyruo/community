package com.nowcoder.community.im.realtime.service;

import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.common.command.SendRoomTextCommand;
import com.nowcoder.community.im.common.ws.AckFrame;
import com.nowcoder.community.im.common.ws.RejectFrame;
import com.nowcoder.community.im.realtime.kafka.CommandProducer;
import com.nowcoder.community.im.realtime.presence.WsConnection;
import com.nowcoder.community.im.realtime.support.ConversationIdSupport;
import com.nowcoder.community.im.realtime.ws.ImFrameCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class MessageCommandIngressService {

    private static final Logger log = LoggerFactory.getLogger(MessageCommandIngressService.class);

    private final CommandProducer commandProducer;
    private final ImFrameCodec frameCodec;
    private final long kafkaSendTimeoutMs;

    public MessageCommandIngressService(CommandProducer commandProducer, ImFrameCodec frameCodec) {
        this(commandProducer, frameCodec, 5000L);
    }

    @Autowired
    public MessageCommandIngressService(
            CommandProducer commandProducer,
            ImFrameCodec frameCodec,
            @Value("${im.ws.kafka-send-timeout-ms:5000}") long kafkaSendTimeoutMs
    ) {
        this.commandProducer = commandProducer;
        this.frameCodec = frameCodec;
        this.kafkaSendTimeoutMs = Math.max(1L, kafkaSendTimeoutMs);
    }

    public Mono<Void> sendPrivate(WsConnection conn, UUID toUserId, String clientMsgId, String content) {
        String requestId = newRequestId();
        SendPrivateTextCommand command = new SendPrivateTextCommand(
                requestId,
                clientMsgId,
                conn.userId(),
                toUserId,
                ConversationIdSupport.conversationId(conn.userId(), toUserId),
                content,
                System.currentTimeMillis()
        );
        attachSendResult(conn, "sendPrivateText", clientMsgId, requestId, commandProducer.sendPrivateText(command));
        return Mono.empty();
    }

    public Mono<Void> sendRoom(WsConnection conn, UUID roomId, String clientMsgId, String content) {
        String requestId = newRequestId();
        SendRoomTextCommand command = new SendRoomTextCommand(
                requestId,
                clientMsgId,
                conn.userId(),
                roomId,
                content,
                System.currentTimeMillis()
        );
        attachSendResult(conn, "sendRoomText", clientMsgId, requestId, commandProducer.sendRoomText(command));
        return Mono.empty();
    }

    private void attachSendResult(
            WsConnection conn,
            String cmd,
            String clientMsgId,
            String requestId,
            CompletableFuture<?> future
    ) {
        if (future == null) {
            sendReject(conn, cmd, clientMsgId, requestId, 503, "kafka_send_failed", "kafka send failed");
            return;
        }
        AtomicBoolean completed = new AtomicBoolean(false);
        future.whenComplete((ignored, error) -> {
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            if (error != null) {
                log.warn("IM command enqueue completed exceptionally for cmd={} requestId={}", cmd, requestId, error);
                sendReject(conn, cmd, clientMsgId, requestId, 503, "kafka_send_failed", "kafka send failed");
                return;
            }
            conn.trySendText(frameCodec.write(new AckFrame("ack", cmd, clientMsgId, requestId)));
        });
        CompletableFuture.delayedExecutor(kafkaSendTimeoutMs, TimeUnit.MILLISECONDS).execute(() -> {
            if (future.isDone() || !completed.compareAndSet(false, true)) {
                return;
            }
            log.warn("IM command enqueue timed out for cmd={} requestId={} timeoutMs={}", cmd, requestId, kafkaSendTimeoutMs);
            sendReject(conn, cmd, clientMsgId, requestId, 503, "kafka_send_timeout", "kafka send timeout");
        });
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
                cmd,
                clientMsgId == null ? "" : clientMsgId,
                requestId == null ? "" : requestId,
                code,
                reasonCode,
                message == null ? "" : message
        )));
    }

    private static String newRequestId() {
        return UUID.randomUUID().toString();
    }
}
