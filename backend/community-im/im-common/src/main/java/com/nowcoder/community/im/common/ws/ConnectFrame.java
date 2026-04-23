package com.nowcoder.community.im.common.ws;

public record ConnectFrame(String type, String ticket) {

    public ConnectFrame {
        requireType(type, "connect");
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("type must be " + expected);
        }
    }
}
