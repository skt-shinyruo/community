package com.nowcoder.community.im.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.im.common.command.SendPrivateTextCommandV1;
import com.nowcoder.community.im.common.command.SendRoomTextCommandV1;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEventV1;
import com.nowcoder.community.im.common.event.RoomMemberChangedEventV1;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEventV1;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonContractsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void command_roundtrip_privateText() throws Exception {
        UUID fromUserId = uuid(12);
        UUID toUserId = uuid(99);
        SendPrivateTextCommandV1 cmd = new SendPrivateTextCommandV1(
                "req-1",
                "cmsg-1",
                fromUserId,
                toUserId,
                conversationId(fromUserId, toUserId),
                "hello",
                1700000000000L
        );

        String json = objectMapper.writeValueAsString(cmd);
        SendPrivateTextCommandV1 back = objectMapper.readValue(json, SendPrivateTextCommandV1.class);
        assertEquals(cmd, back);
    }

    @Test
    void command_roundtrip_roomText() throws Exception {
        UUID fromUserId = uuid(12);
        SendRoomTextCommandV1 cmd = new SendRoomTextCommandV1(
                "req-2",
                "cmsg-2",
                fromUserId,
                uuid(1001),
                "hello room",
                1700000000001L
        );

        String json = objectMapper.writeValueAsString(cmd);
        SendRoomTextCommandV1 back = objectMapper.readValue(json, SendRoomTextCommandV1.class);
        assertEquals(cmd, back);
    }

    @Test
    void event_roundtrip_privatePersisted() throws Exception {
        UUID fromUserId = uuid(12);
        UUID toUserId = uuid(99);
        PrivateMessagePersistedEventV1 event = new PrivateMessagePersistedEventV1(
                "evt-1",
                conversationId(fromUserId, toUserId),
                7L,
                uuid(10001),
                fromUserId,
                toUserId,
                "hello",
                1700000001000L
        );

        String json = objectMapper.writeValueAsString(event);
        PrivateMessagePersistedEventV1 back = objectMapper.readValue(json, PrivateMessagePersistedEventV1.class);
        assertEquals(event, back);
    }

    @Test
    void event_roundtrip_roomPersisted() throws Exception {
        UUID fromUserId = uuid(12);
        RoomMessagePersistedEventV1 event = new RoomMessagePersistedEventV1(
                "evt-2",
                uuid(1001),
                7L,
                uuid(20001),
                fromUserId,
                1700000002000L
        );

        String json = objectMapper.writeValueAsString(event);
        RoomMessagePersistedEventV1 back = objectMapper.readValue(json, RoomMessagePersistedEventV1.class);
        assertEquals(event, back);
    }

    @Test
    void event_roundtrip_roomMemberChanged() throws Exception {
        RoomMemberChangedEventV1 event = new RoomMemberChangedEventV1(
                "evt-3",
                uuid(1001),
                uuid(12),
                "JOINED",
                1700000003000L
        );

        String json = objectMapper.writeValueAsString(event);
        RoomMemberChangedEventV1 back = objectMapper.readValue(json, RoomMemberChangedEventV1.class);
        assertEquals(event, back);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static String conversationId(UUID userId1, UUID userId2) {
        UUID first = userId1.compareTo(userId2) <= 0 ? userId1 : userId2;
        UUID second = first.equals(userId1) ? userId2 : userId1;
        return first + "_" + second;
    }
}
