package com.nowcoder.community.im.common.ws;

import java.util.UUID;

public record SendPrivateTextFrame(
        String type,
        String clientMsgId,
        UUID toUserId,
        String content
) {

    public SendPrivateTextFrame {
        requireType(type, "sendPrivateText");
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("type must be " + expected);
        }
    }
}
