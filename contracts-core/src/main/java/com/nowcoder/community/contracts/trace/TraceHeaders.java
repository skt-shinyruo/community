package com.nowcoder.community.contracts.trace;

/**
 * 跨服务 trace 相关 HTTP Header 常量（SSOT）。
 *
 * <p>说明：该类属于稳定契约，不应在 gateway/common 等模块各自重复定义，避免漂移。</p>
 */
public final class TraceHeaders {

    private TraceHeaders() {
    }

    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_TRACEPARENT = "traceparent";
}

