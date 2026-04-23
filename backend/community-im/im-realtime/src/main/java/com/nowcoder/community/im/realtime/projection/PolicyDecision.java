package com.nowcoder.community.im.realtime.projection;

public record PolicyDecision(boolean allowed, int code, String reasonCode, String message) {

    public static PolicyDecision allow() {
        return new PolicyDecision(true, 0, "", "OK");
    }

    public static PolicyDecision deny(int code, String reasonCode, String message) {
        return new PolicyDecision(false, code, String.valueOf(reasonCode), String.valueOf(message));
    }
}
