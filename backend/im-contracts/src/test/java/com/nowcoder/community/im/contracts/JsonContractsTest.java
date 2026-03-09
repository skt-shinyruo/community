package com.nowcoder.community.im.contracts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.im.contracts.command.SendPrivateTextCommandV1;
import com.nowcoder.community.im.contracts.event.PrivateMessagePersistedEventV1;
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
}

