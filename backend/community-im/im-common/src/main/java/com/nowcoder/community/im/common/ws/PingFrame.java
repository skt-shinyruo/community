package com.nowcoder.community.im.common.ws;

public record PingFrame(String type, long sentAtEpochMillis) {

    public PingFrame {
        requireType(type, "ping");
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("type must be " + expected);
        }
    }
}
