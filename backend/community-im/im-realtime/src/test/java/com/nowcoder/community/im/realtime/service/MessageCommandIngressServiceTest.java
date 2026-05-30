package com.nowcoder.community.im.realtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.realtime.kafka.CommandProducer;
import com.nowcoder.community.im.realtime.presence.WsConnection;
import com.nowcoder.community.im.realtime.ws.ImFrameCodec;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

class MessageCommandIngressServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendPrivate_shouldWaitForKafkaSendSuccessBeforeAck() throws Exception {
        CommandProducer commandProducer = Mockito.mock(CommandProducer.class);
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        Mockito.when(commandProducer.sendPrivateText(any(SendPrivateTextCommand.class))).thenReturn(future);

        MessageCommandIngressService service = new MessageCommandIngressService(commandProducer, new ImFrameCodec(jsonCodec()));
        WsConnection connection = newConnection(uuid(1));

        service.sendPrivate(connection, uuid(2), "c1", "hello").block(Duration.ofSeconds(1));

        assertThat(connection.outboundBacklog()).isZero();

        future.complete(null);

        JsonNode ackFrame = objectMapper.readTree(connection.outboundSink().asFlux().next().block(Duration.ofSeconds(1)));
        assertThat(ackFrame.path("type").asText("")).isEqualTo("ack");
        assertThat(ackFrame.path("cmd").asText("")).isEqualTo("sendPrivateText");
        assertThat(ackFrame.path("clientMsgId").asText("")).isEqualTo("c1");
        assertThat(ackFrame.path("requestId").asText("")).isNotBlank();
    }

    @Test
    void sendPrivate_shouldRejectWhenKafkaSendFailsAsync() throws Exception {
        CommandProducer commandProducer = Mockito.mock(CommandProducer.class);
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        Mockito.when(commandProducer.sendPrivateText(any(SendPrivateTextCommand.class))).thenReturn(future);

        MessageCommandIngressService service = new MessageCommandIngressService(commandProducer, new ImFrameCodec(jsonCodec()));
        WsConnection connection = newConnection(uuid(1));

        service.sendPrivate(connection, uuid(2), "c2", "hello").block(Duration.ofSeconds(1));
        future.completeExceptionally(new IllegalStateException("broker unavailable"));

        JsonNode rejectFrame = objectMapper.readTree(connection.outboundSink().asFlux().next().block(Duration.ofSeconds(1)));
        assertThat(rejectFrame.path("type").asText("")).isEqualTo("reject");
        assertThat(rejectFrame.path("cmd").asText("")).isEqualTo("sendPrivateText");
        assertThat(rejectFrame.path("clientMsgId").asText("")).isEqualTo("c2");
        assertThat(rejectFrame.path("reasonCode").asText("")).isEqualTo("kafka_send_failed");
        assertThat(rejectFrame.path("requestId").asText("")).isNotBlank();
    }

    @Test
    void sendPrivate_shouldRejectWhenKafkaSendDoesNotCompleteBeforeTimeout() throws Exception {
        CommandProducer commandProducer = Mockito.mock(CommandProducer.class);
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        Mockito.when(commandProducer.sendPrivateText(any(SendPrivateTextCommand.class))).thenReturn(future);

        MessageCommandIngressService service = new MessageCommandIngressService(
                commandProducer,
                new ImFrameCodec(jsonCodec()),
                10L
        );
        WsConnection connection = newConnection(uuid(1));

        service.sendPrivate(connection, uuid(2), "c3", "hello").block(Duration.ofSeconds(1));

        JsonNode rejectFrame = objectMapper.readTree(connection.outboundSink().asFlux().next().block(Duration.ofSeconds(1)));
        assertThat(rejectFrame.path("type").asText("")).isEqualTo("reject");
        assertThat(rejectFrame.path("cmd").asText("")).isEqualTo("sendPrivateText");
        assertThat(rejectFrame.path("clientMsgId").asText("")).isEqualTo("c3");
        assertThat(rejectFrame.path("reasonCode").asText("")).isEqualTo("kafka_send_timeout");
        assertThat(rejectFrame.path("requestId").asText("")).isNotBlank();

        assertThat(connection.outboundBacklog()).isEqualTo(1);
        future.complete(null);
        assertThat(connection.outboundBacklog()).isEqualTo(1);
    }

    private WsConnection newConnection(UUID userId) {
        WebSocketSession session = Mockito.mock(WebSocketSession.class);
        Mockito.when(session.close()).thenReturn(Mono.empty());
        WsConnection connection = new WsConnection("c1", session, 10);
        connection.bindUser(userId);
        return connection;
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static JacksonJsonCodec jsonCodec() {
        return new JacksonJsonCodec(JsonMappers.standard());
    }
}
