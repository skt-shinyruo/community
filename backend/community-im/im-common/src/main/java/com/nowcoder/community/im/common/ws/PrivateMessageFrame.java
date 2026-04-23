package com.nowcoder.community.im.common.ws;

import java.util.UUID;

public record PrivateMessageFrame(
        String type,
        String conversationId,
        long seq,
        UUID messageId,
        UUID fromUserId,
        UUID toUserId,
        String content,
        long createdAtEpochMillis
) {

    public PrivateMessageFrame {
        requireType(type, "privateMessage");
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("type must be " + expected);
        }
    }
}
