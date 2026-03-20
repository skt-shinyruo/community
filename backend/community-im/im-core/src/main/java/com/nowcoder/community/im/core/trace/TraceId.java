package com.nowcoder.community.im.core.trace;

public final class TraceId {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceId() {
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void set(String traceId) {
        CURRENT.set(traceId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static String generate() {
        return TraceIdCodec.generateTraceId();
    }
}

