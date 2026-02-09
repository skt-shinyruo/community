package com.nowcoder.community.common.dubbo;

// Dubbo 调用附件（attachment）key 约定：用于 traceId 等上下文在 RPC 调用链路中透传。
import com.nowcoder.community.common.web.TraceIdFilter;

public final class DubboAttachmentKeys {

    private DubboAttachmentKeys() {
    }

    public static final String TRACE_ID = TraceIdFilter.HEADER_TRACE_ID;
    public static final String TRACEPARENT = TraceIdFilter.HEADER_TRACEPARENT;
}

