package com.nowcoder.community.im.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.im.common.command.SendPrivateTextCommandV1;
import com.nowcoder.community.im.common.command.SendRoomTextCommandV1;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEventV1;
import com.nowcoder.community.im.common.event.RoomMemberChangedEventV1;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEventV1;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonContractsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void command_roundtrip_privateText() throws Exception {
        SendPrivateTextCommandV1 cmd = new SendPrivateTextCommandV1(
                "req-1",
                "cmsg-1",
                12,
                99,
                "12_99",
                "hello",
                1700000000000L
        );

        String json = objectMapper.writeValueAsString(cmd);
        SendPrivateTextCommandV1 back = objectMapper.readValue(json, SendPrivateTextCommandV1.class);
        assertEquals(cmd, back);
    }

    @Test
    void command_roundtrip_roomText() throws Exception {
        SendRoomTextCommandV1 cmd = new SendRoomTextCommandV1(
                "req-2",
                "cmsg-2",
                12,
                1001L,
                "hello room",
                1700000000001L
        );

        String json = objectMapper.writeValueAsString(cmd);
        SendRoomTextCommandV1 back = objectMapper.readValue(json, SendRoomTextCommandV1.class);
        assertEquals(cmd, back);
    }

    @Test
    void event_roundtrip_privatePersisted() throws Exception {
        PrivateMessagePersistedEventV1 event = new PrivateMessagePersistedEventV1(
                "evt-1",
                "12_99",
                7L,
                10001L,
                12,
                99,
                "hello",
                1700000001000L
        );

        String json = objectMapper.writeValueAsString(event);
        PrivateMessagePersistedEventV1 back = objectMapper.readValue(json, PrivateMessagePersistedEventV1.class);
        assertEquals(event, back);
    }

    @Test
    void event_roundtrip_roomPersisted() throws Exception {
        RoomMessagePersistedEventV1 event = new RoomMessagePersistedEventV1(
                "evt-2",
                1001L,
                7L,
                20001L,
                12,
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
                1001L,
                12,
                "JOINED",
                1700000003000L
        );

        String json = objectMapper.writeValueAsString(event);
        RoomMemberChangedEventV1 back = objectMapper.readValue(json, RoomMemberChangedEventV1.class);
        assertEquals(event, back);
    }
}
