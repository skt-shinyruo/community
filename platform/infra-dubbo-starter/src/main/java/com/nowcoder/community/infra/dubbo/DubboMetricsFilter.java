package com.nowcoder.community.infra.dubbo;

// Dubbo 调用指标 Filter：统一记录 RPC 调用计数与时延（consumer/provider），便于 Prometheus/Grafana 观测与告警。
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcServiceContext;

import java.util.concurrent.TimeUnit;

@Activate(group = {CommonConstants.CONSUMER, CommonConstants.PROVIDER}, order = -9_000)
public class DubboMetricsFilter implements Filter {

    private static final String METRIC_TOTAL = "dubbo_rpc_requests_total";
    private static final String METRIC_LATENCY = "dubbo_rpc_latency";

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        long startNanos = System.nanoTime();

        String side = "unknown";
        RpcServiceContext ctx = RpcContext.getServiceContext();
        if (ctx != null) {
            if (ctx.isConsumerSide()) {
                side = "consumer";
            } else if (ctx.isProviderSide()) {
                side = "provider";
            }
        }

        String service = invoker == null || invoker.getInterface() == null ? "unknown" : invoker.getInterface().getSimpleName();
        String method = invocation == null ? "unknown" : String.valueOf(invocation.getMethodName());

        try {
            Result result = invoker.invoke(invocation);
            String outcome = outcomeOf(result);
            record(side, service, method, outcome, startNanos);
            return result;
        } catch (RuntimeException e) {
            String outcome = isTimeout(e) ? "timeout" : "error";
            record(side, service, method, outcome, startNanos);
            throw e;
        }
    }

    private void record(String side, String service, String method, String outcome, long startNanos) {
        MeterRegistry registry = Metrics.globalRegistry;
        if (registry == null) {
            return;
        }
        Tags tags = Tags.of(
                "side", safe(side),
                "service", safe(service),
                "method", safe(method),
                "outcome", safe(outcome)
        );
        registry.counter(METRIC_TOTAL, tags).increment();
        registry.timer(METRIC_LATENCY, tags).record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    private String outcomeOf(Result result) {
        if (result == null) {
            return "error";
        }
        if (!result.hasException()) {
            return "success";
        }
        Throwable ex = result.getException();
        return isTimeout(ex) ? "timeout" : "error";
    }

    private boolean isTimeout(Throwable t) {
        if (t == null) {
            return false;
        }
        if (t instanceof RpcException re) {
            return re.isTimeout();
        }
        return String.valueOf(t.getMessage()).toLowerCase().contains("timeout");
    }

    private String safe(String s) {
        return s == null || s.isBlank() ? "unknown" : s.trim();
    }
}

