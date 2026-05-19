package com.nowcoder.community.im.realtime.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.im.common.event.PrivateMessageCommittedEvent;
import com.nowcoder.community.im.common.event.RoomMessageCommittedEvent;
import com.nowcoder.community.im.realtime.presence.ConnectionRegistry;
import com.nowcoder.community.im.realtime.presence.WsConnection;
import com.nowcoder.community.im.realtime.ws.ImFrameCodec;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SendResultPushServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConnectionRegistry connectionRegistry = new ConnectionRegistry();
    private final SendResultPushService service = new SendResultPushService(
            connectionRegistry,
            new ImFrameCodec(objectMapper)
    );

    @Test
    void pushPrivateCommittedUsesSendAttemptIdentityInCommittedFrame() throws Exception {
        UUID fromUserId = uuid(1);
        WsConnection connection = registeredConnection(fromUserId);
        UUID messageId = uuid(100);

        service.pushPrivateCommitted(new PrivateMessageCommittedEvent(
                "im:psr:test",
                "req-private-1",
                "c-private-1",
                fromUserId,
                uuid(2),
                "conv-1",
                messageId,
                7L,
                123L
        ));

        JsonNode frame = nextFrame(connection);
        assertThat(frame.path("type").asText()).isEqualTo("committed");
        assertThat(frame.path("cmd").asText()).isEqualTo("sendPrivateText");
        assertThat(frame.path("requestId").asText()).isEqualTo("req-private-1");
        assertThat(frame.path("clientMsgId").asText()).isEqualTo("c-private-1");
        assertThat(frame.path("conversationId").asText()).isEqualTo("conv-1");
        assertThat(frame.path("messageId").asText()).isEqualTo(messageId.toString());
        assertThat(frame.path("seq").asLong()).isEqualTo(7L);
    }

    @Test
    void pushRoomCommittedUsesSendAttemptIdentityInCommittedFrame() throws Exception {
        UUID fromUserId = uuid(11);
        WsConnection connection = registeredConnection(fromUserId);
        UUID roomId = uuid(12);
        UUID messageId = uuid(120);

        service.pushRoomCommitted(new RoomMessageCommittedEvent(
                "im:rsr:test",
                "req-room-1",
                "c-room-1",
                fromUserId,
                roomId,
                messageId,
                9L,
                456L
        ));

        JsonNode frame = nextFrame(connection);
        assertThat(frame.path("type").asText()).isEqualTo("committed");
        assertThat(frame.path("cmd").asText()).isEqualTo("sendRoomText");
        assertThat(frame.path("requestId").asText()).isEqualTo("req-room-1");
        assertThat(frame.path("clientMsgId").asText()).isEqualTo("c-room-1");
        assertThat(frame.path("roomId").asText()).isEqualTo(roomId.toString());
        assertThat(frame.path("messageId").asText()).isEqualTo(messageId.toString());
        assertThat(frame.path("seq").asLong()).isEqualTo(9L);
    }

    private JsonNode nextFrame(WsConnection connection) throws Exception {
        String json = connection.outboundSink().asFlux().next().block(Duration.ofSeconds(1));
        return objectMapper.readTree(json);
    }

    private WsConnection registeredConnection(UUID userId) {
        WebSocketSession session = Mockito.mock(WebSocketSession.class);
        Mockito.when(session.close()).thenReturn(Mono.empty());
        WsConnection connection = new WsConnection("conn-" + userId, session, 10);
        connection.bindUser(userId);
        connectionRegistry.register(connection);
        return connection;
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
