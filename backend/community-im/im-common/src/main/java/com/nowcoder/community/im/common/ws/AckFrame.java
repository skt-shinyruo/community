package com.nowcoder.community.im.common.ws;

public record AckFrame(
        String type,
        String cmd,
        String clientMsgId,
        String requestId
) {

    public AckFrame {
        requireType(type, "ack");
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("type must be " + expected);
        }
    }
}
