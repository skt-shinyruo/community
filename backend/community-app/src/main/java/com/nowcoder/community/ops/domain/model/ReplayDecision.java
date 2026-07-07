package com.nowcoder.community.ops.domain.model;

public record ReplayDecision(boolean allowed, String result, String reason) {

    public static ReplayDecision allow() {
        return new ReplayDecision(true, "REPLAYED", "allowed");
    }

    public static ReplayDecision reject(String reason) {
        return new ReplayDecision(false, "MANUAL_REPAIR_REQUIRED", reason);
    }
}
