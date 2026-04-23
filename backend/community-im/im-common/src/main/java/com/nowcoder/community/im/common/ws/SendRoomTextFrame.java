package com.nowcoder.community.im.common.ws;

import java.util.UUID;

public record SendRoomTextFrame(
        String type,
        String clientMsgId,
        UUID roomId,
        String content
) {

    public SendRoomTextFrame {
        requireType(type, "sendRoomText");
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("type must be " + expected);
        }
    }
}
