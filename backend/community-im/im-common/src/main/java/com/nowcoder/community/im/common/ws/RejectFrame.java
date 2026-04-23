package com.nowcoder.community.im.common.ws;

public record RejectFrame(
        String type,
        String cmd,
        String clientMsgId,
        String requestId,
        int code,
        String reasonCode,
        String message
) {

    public RejectFrame {
        requireType(type, "reject");
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("type must be " + expected);
        }
    }
}
