package com.nowcoder.community.im.common.policy;

public record PrivateMessagePolicyDecision(
        boolean allowed,
        int code,
        String reasonCode,
        String message,
        long decidedAtEpochMs
) {

    public static PrivateMessagePolicyDecision allow() {
        return new PrivateMessagePolicyDecision(true, 0, "allowed", "", System.currentTimeMillis());
    }

    public static PrivateMessagePolicyDecision deny(int code, String reasonCode, String message) {
        return new PrivateMessagePolicyDecision(
                false,
                code,
                reasonCode == null || reasonCode.isBlank() ? "policy_denied" : reasonCode,
                message == null ? "" : message,
                System.currentTimeMillis()
        );
    }
}
