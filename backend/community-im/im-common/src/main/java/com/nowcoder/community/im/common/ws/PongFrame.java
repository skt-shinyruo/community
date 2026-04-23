package com.nowcoder.community.im.common.ws;

public record PongFrame(String type, long sentAtEpochMillis) {

    public PongFrame {
        requireType(type, "pong");
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("type must be " + expected);
        }
    }
}
