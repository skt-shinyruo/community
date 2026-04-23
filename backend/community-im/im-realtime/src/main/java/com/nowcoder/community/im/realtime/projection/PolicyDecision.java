package com.nowcoder.community.im.realtime.projection;

public record PolicyDecision(boolean allowed, int code, String message) {

    public static PolicyDecision allow() {
        return new PolicyDecision(true, 0, "OK");
    }

    public static PolicyDecision deny(int code, String message) {
        return new PolicyDecision(false, code, String.valueOf(message));
    }
}
