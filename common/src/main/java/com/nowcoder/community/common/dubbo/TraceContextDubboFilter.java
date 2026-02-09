package com.nowcoder.community.common.dubbo;

// Dubbo traceId 透传 Filter：consumer 写入 attachment；provider 注入 TraceContext/MDC 并 finally 清理，避免线程复用串线。
import com.nowcoder.community.common.trace.TraceContext;
import com.nowcoder.community.common.trace.TraceId;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcServiceContext;
import org.springframework.util.StringUtils;

@Activate(group = {CommonConstants.CONSUMER, CommonConstants.PROVIDER}, order = -10_000)
public class TraceContextDubboFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        if (invoker == null) {
            throw new RpcException("invoker 不能为空");
        }

        RpcServiceContext ctx = RpcContext.getServiceContext();
        if (ctx != null && ctx.isConsumerSide()) {
            return invokeConsumer(invoker, invocation);
        }
        if (ctx != null && ctx.isProviderSide()) {
            return invokeProvider(invoker, invocation);
        }
        return invoker.invoke(invocation);
    }

    private Result invokeConsumer(Invoker<?> invoker, Invocation invocation) {
        String before = TraceId.get();
        boolean created = false;
        String traceId = before;
        if (!StringUtils.hasText(traceId)) {
            traceId = TraceId.generate();
            created = true;
            TraceContext.set(traceId);
        }

        try {
            if (invocation != null && !StringUtils.hasText(invocation.getAttachment(DubboAttachmentKeys.TRACE_ID))) {
                invocation.setAttachment(DubboAttachmentKeys.TRACE_ID, traceId);
            }
            return invoker.invoke(invocation);
        } finally {
            if (created) {
                TraceContext.clear();
            }
        }
    }

    private Result invokeProvider(Invoker<?> invoker, Invocation invocation) {
        String traceId = invocation == null ? null : invocation.getAttachment(DubboAttachmentKeys.TRACE_ID);
        if (!StringUtils.hasText(traceId)) {
            traceId = TraceId.generate();
        }

        TraceContext.set(traceId);
        try {
            return invoker.invoke(invocation);
        } finally {
            TraceContext.clear();
        }
    }
}

