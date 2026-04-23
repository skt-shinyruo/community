package com.nowcoder.community.im.common.ws;

public record ConnectedFrame(
        String type,
        String sessionId,
        String workerId
) {

    public ConnectedFrame {
        requireType(type, "connected");
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("type must be " + expected);
        }
    }
}
