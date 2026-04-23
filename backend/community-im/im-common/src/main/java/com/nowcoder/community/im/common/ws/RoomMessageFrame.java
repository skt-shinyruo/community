package com.nowcoder.community.im.common.ws;

import java.util.UUID;

public record RoomMessageFrame(
        String type,
        UUID roomId,
        long seq,
        UUID messageId,
        UUID fromUserId,
        long createdAtEpochMillis
) {

    public RoomMessageFrame {
        requireType(type, "roomMessage");
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("type must be " + expected);
        }
    }
}
