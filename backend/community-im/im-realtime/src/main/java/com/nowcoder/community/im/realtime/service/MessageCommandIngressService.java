package com.nowcoder.community.im.realtime.service;

import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.common.command.SendRoomTextCommand;
import com.nowcoder.community.im.common.ws.AckFrame;
import com.nowcoder.community.im.common.ws.RejectFrame;
import com.nowcoder.community.im.realtime.kafka.CommandProducer;
import com.nowcoder.community.im.realtime.presence.WsConnection;
import com.nowcoder.community.im.realtime.support.ConversationIdSupport;
import com.nowcoder.community.im.realtime.ws.ImFrameCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class MessageCommandIngressService {

    private static final Logger log = LoggerFactory.getLogger(MessageCommandIngressService.class);

    private final CommandProducer commandProducer;
    private final ImFrameCodec frameCodec;

    public MessageCommandIngressService(CommandProducer commandProducer, ImFrameCodec frameCodec) {
        this.commandProducer = commandProducer;
        this.frameCodec = frameCodec;
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
        conn.trySendText(frameCodec.write(new AckFrame("ack", cmd, clientMsgId, requestId)));
        future.whenComplete((ignored, error) -> {
            if (error != null) {
                log.warn("IM command enqueue completed exceptionally for cmd={} requestId={}", cmd, requestId, error);
            }
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
