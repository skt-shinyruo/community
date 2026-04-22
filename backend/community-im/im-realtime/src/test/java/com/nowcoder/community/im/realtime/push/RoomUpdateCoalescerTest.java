package com.nowcoder.community.im.realtime.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.im.realtime.presence.ConnectionRegistry;
import com.nowcoder.community.im.realtime.presence.WsConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoomUpdateCoalescerTest {

    private final ConnectionRegistry connectionRegistry = new ConnectionRegistry();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RoomUpdateCoalescer coalescer;

    @AfterEach
    void tearDown() {
        if (coalescer != null) {
            coalescer.destroy();
        }
    }

    @Test
    void shouldCoalesceToLatestSeqPerRoomPerConnection() throws Exception {
        coalescer = new RoomUpdateCoalescer(connectionRegistry, objectMapper, 20);

        WebSocketSession session = Mockito.mock(WebSocketSession.class);
        Mockito.when(session.close()).thenReturn(Mono.empty());

        WsConnection conn = new WsConnection("c1", session, 100);
        conn.bindUser(uuid(1));
        connectionRegistry.register(conn);

        UUID roomId10 = uuid(10);
        UUID roomId11 = uuid(11);
        coalescer.markRoomUpdated(conn, roomId10, 1L);
        coalescer.markRoomUpdated(conn, roomId10, 2L);
        coalescer.markRoomUpdated(conn, roomId11, 5L);

        String json = conn.outboundSink().asFlux()
                .next()
                .block(Duration.ofSeconds(2));

        assertThat(json).isNotBlank();
        JsonNode node = objectMapper.readTree(json);
        assertThat(node.path("type").asText("")).isEqualTo("roomUpdatedBatch");
        assertThat(node.path("items").isArray()).isTrue();

        // room 10 should be latest=2
        boolean room10Ok = false;
        boolean room11Ok = false;
        for (JsonNode item : node.path("items")) {
            String roomId = item.path("roomId").asText("");
            long lastSeq = item.path("lastSeq").asLong();
            if (roomId10.toString().equals(roomId)) {
                room10Ok = lastSeq == 2L;
            }
            if (roomId11.toString().equals(roomId)) {
                room11Ok = lastSeq == 5L;
            }
        }
        assertThat(room10Ok).isTrue();
        assertThat(room11Ok).isTrue();
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
