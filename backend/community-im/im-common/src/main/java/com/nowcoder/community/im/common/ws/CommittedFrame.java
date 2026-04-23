package com.nowcoder.community.im.common.ws;

public record CommittedFrame(
        String type,
        String cmd,
        String clientMsgId,
        String requestId
) {

    public CommittedFrame {
        requireType(type, "committed");
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("type must be " + expected);
        }
    }
}
