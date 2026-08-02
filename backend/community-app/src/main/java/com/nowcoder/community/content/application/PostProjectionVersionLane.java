package com.nowcoder.community.content.application;

public enum PostProjectionVersionLane {
    POST(true),
    LEGACY_POST(false),
    COMMENT(false),
    SOCIAL(false);

    private final boolean monotonicSourceVersion;

    PostProjectionVersionLane(boolean monotonicSourceVersion) {
        this.monotonicSourceVersion = monotonicSourceVersion;
    }

    public boolean hasMonotonicSourceVersion() {
        return monotonicSourceVersion;
    }
}
