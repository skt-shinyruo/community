package com.nowcoder.community.im.realtime.service;

import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.common.command.SendRoomTextCommand;
import com.nowcoder.community.im.realtime.kafka.CommandProducer;
import com.nowcoder.community.im.realtime.presence.WsConnection;
import com.nowcoder.community.im.common.support.ConversationIdSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/**
 * Enqueue ingress for IM private/room send commands.
 *
 * <p>Each invocation of {@link #sendPrivate}/{@link #sendRoom} is one send attempt and returns a
 * cold {@link Mono} that emits exactly one terminal {@link CommandIngressResult}:
 * {@code ACK} once Kafka accepts the command, {@code REJECT} when the enqueue future fails or
 * does not complete within {@code im.ws.kafka-send-timeout-ms}. The ingress takes the sender id
 * rather than the {@link WsConnection} and can therefore never write to the connection; the
 * caller maps the terminal to a WebSocket ack/reject frame.
 *
 * <p>Reactive semantics:
 * <ul>
 *   <li>Exactly-one terminal: the underlying Mono completes after its single emission; a Kafka
 *   completion arriving after a timeout terminal is dropped by the {@code timeout} operator.</li>
 *   <li>Cancellation: cancelling the subscription cancels the observation of the Kafka future
 *   and disposes the timeout, so no terminal is emitted afterwards and no connection or callback
 *   is retained in the background. Cancelling the observation does not retract a command the
 *   broker may already have accepted.</li>
 *   <li>Backpressure: the Mono is scalar (at most one element) and defers emission to downstream
 *   demand; no buffering is involved.</li>
 *   <li>Laziness: the command is built and the Kafka send is issued on subscription; an
 *   unsubscribed Mono has no side effects.</li>
 * </ul>
 */
@Service
public class MessageCommandIngressService {

    private static final Logger log = LoggerFactory.getLogger(MessageCommandIngressService.class);

    private final CommandProducer commandProducer;
    private final long kafkaSendTimeoutMs;

    public MessageCommandIngressService(CommandProducer commandProducer) {
        this(commandProducer, 5000L);
    }

    @Autowired
    public MessageCommandIngressService(
            CommandProducer commandProducer,
            @Value("${im.ws.kafka-send-timeout-ms:5000}") long kafkaSendTimeoutMs
    ) {
        this.commandProducer = commandProducer;
        this.kafkaSendTimeoutMs = Math.max(1L, kafkaSendTimeoutMs);
    }

    public Mono<CommandIngressResult> sendPrivate(UUID fromUserId, UUID toUserId, String clientMsgId, String content) {
        return Mono.defer(() -> {
            String requestId = newRequestId();
            SendPrivateTextCommand command = new SendPrivateTextCommand(
                    requestId,
                    clientMsgId,
                    fromUserId,
                    toUserId,
                    ConversationIdSupport.conversationId(fromUserId, toUserId),
                    content,
                    System.currentTimeMillis()
            );
            return enqueue("sendPrivateText", clientMsgId, requestId, commandProducer.sendPrivateText(command));
        });
    }

    public Mono<CommandIngressResult> sendRoom(UUID fromUserId, UUID roomId, String clientMsgId, String content) {
        return Mono.defer(() -> {
            String requestId = newRequestId();
            SendRoomTextCommand command = new SendRoomTextCommand(
                    requestId,
                    clientMsgId,
                    fromUserId,
                    roomId,
                    content,
                    System.currentTimeMillis()
            );
            return enqueue("sendRoomText", clientMsgId, requestId, commandProducer.sendRoomText(command));
        });
    }

    private Mono<CommandIngressResult> enqueue(
            String cmd,
            String clientMsgId,
            String requestId,
            CompletableFuture<?> future
    ) {
        if (future == null) {
            return Mono.just(CommandIngressResult.rejected(cmd, clientMsgId, requestId, 503, "kafka_send_failed", "kafka send failed"));
        }
        return Mono.fromFuture(future)
                .timeout(Duration.ofMillis(kafkaSendTimeoutMs))
                .then(Mono.just(CommandIngressResult.acked(cmd, clientMsgId, requestId)))
                .onErrorResume(TimeoutException.class, e -> {
                    log.warn("IM command enqueue timed out for cmd={} requestId={} timeoutMs={}", cmd, requestId, kafkaSendTimeoutMs);
                    return Mono.just(CommandIngressResult.rejected(cmd, clientMsgId, requestId, 503, "kafka_send_timeout", "kafka send timeout"));
                })
                .onErrorResume(e -> {
                    log.warn("IM command enqueue completed exceptionally for cmd={} requestId={}", cmd, requestId, e);
                    return Mono.just(CommandIngressResult.rejected(cmd, clientMsgId, requestId, 503, "kafka_send_failed", "kafka send failed"));
                });
    }

    private static String newRequestId() {
        return UUID.randomUUID().toString();
    }
}
