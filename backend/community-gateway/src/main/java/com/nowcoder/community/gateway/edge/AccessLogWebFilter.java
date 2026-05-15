package com.nowcoder.community.gateway.edge;

import com.nowcoder.community.common.logging.EventLogFields;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.trace.TraceIdCodec;
import com.nowcoder.community.common.webflux.TraceIdWebFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public class AccessLogWebFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AccessLogWebFilter.class);
    private static final String MDC_KEY_TRACE_ID = "traceId";
    private static final String MDC_KEY_CATEGORY = EventLogFields.EVENT_CATEGORY;
    private static final String MDC_KEY_ACTION = EventLogFields.EVENT_ACTION;
    private static final String MDC_KEY_OUTCOME = EventLogFields.EVENT_OUTCOME;
    private static final String CATEGORY_ACCESS = "access";
    private static final String ACTION_HTTP_ACCESS = "gateway_http_access";
    static final int ORDER = TraceIdWebFilter.ORDER + 1;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange == null || chain == null) {
            return Mono.empty();
        }
        long startedAt = System.nanoTime();
        String method = exchange.getRequest().getMethod() == null ? "UNKNOWN" : exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        return chain.filter(exchange)
                .doFinally(signal -> {
                    HttpStatusCode status = exchange.getResponse().getStatusCode();
                    int statusCode = status == null ? 0 : status.value();
                    long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
                    String traceId = resolveTraceId(exchange);
                    String previousTraceId = MDC.get(MDC_KEY_TRACE_ID);
                    String previousCategory = MDC.get(MDC_KEY_CATEGORY);
                    String previousAction = MDC.get(MDC_KEY_ACTION);
                    String previousOutcome = MDC.get(MDC_KEY_OUTCOME);
                    try {
                        if (traceId == null || traceId.isBlank()) {
                            MDC.remove(MDC_KEY_TRACE_ID);
                        } else {
                            MDC.put(MDC_KEY_TRACE_ID, traceId);
                        }
                        MDC.put(MDC_KEY_CATEGORY, CATEGORY_ACCESS);
                        MDC.put(MDC_KEY_ACTION, ACTION_HTTP_ACCESS);
                        MDC.put(MDC_KEY_OUTCOME, outcomeForStatus(statusCode));
                        log.info("[gateway-http] method={} path={} status={} durationMs={} traceId={}",
                                method,
                                path,
                                statusCode,
                                durationMs,
                                traceId == null ? "" : traceId);
                    } finally {
                        if (previousTraceId == null || previousTraceId.isBlank()) {
                            MDC.remove(MDC_KEY_TRACE_ID);
                        } else {
                            MDC.put(MDC_KEY_TRACE_ID, previousTraceId);
                        }
                        restore(MDC_KEY_CATEGORY, previousCategory);
                        restore(MDC_KEY_ACTION, previousAction);
                        restore(MDC_KEY_OUTCOME, previousOutcome);
                    }
                });
    }

    private String outcomeForStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return "denied";
        }
        if (statusCode >= 500) {
            return "failure";
        }
        return "success";
    }

    private void restore(String key, String previousValue) {
        if (previousValue == null || previousValue.isBlank()) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, previousValue);
    }

    private String resolveTraceId(ServerWebExchange exchange) {
        String traceparent = exchange.getResponse().getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT);
        if (traceparent == null || traceparent.isBlank()) {
            traceparent = exchange.getRequest().getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT);
        }
        String traceId = TraceIdCodec.extractTraceIdFromTraceparent(traceparent);
        if (traceId == null) {
            return "";
        }
        return traceId;
    }
}
