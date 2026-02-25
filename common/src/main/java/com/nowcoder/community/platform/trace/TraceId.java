package com.nowcoder.community.platform.trace;

import com.nowcoder.community.contracts.trace.TraceIdCodec;

/**
 * traceId 线程上下文（运行期实现）：
 * - 仅用于“当前线程边界”的链路串联（servlet filter、Dubbo filter、Kafka consumer 等）
 * - reactive 场景不应把 ThreadLocal 作为主传递机制，只可在受控边界桥接（例如 Dubbo 调用线程）
 */
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

    /**
     * 生成新的 traceId（32 位小写 hex）。
     */
    public static String generate() {
        return TraceIdCodec.generateTraceId();
    }
}
