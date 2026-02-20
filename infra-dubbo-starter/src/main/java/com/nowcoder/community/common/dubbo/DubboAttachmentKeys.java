package com.nowcoder.community.common.dubbo;

// Dubbo 调用附件（attachment）key 约定：用于 traceId 等上下文在 RPC 调用链路中透传。
public final class DubboAttachmentKeys {

    private DubboAttachmentKeys() {
    }

    public static final String TRACE_ID = "X-Trace-Id";
    public static final String TRACEPARENT = "traceparent";
}

