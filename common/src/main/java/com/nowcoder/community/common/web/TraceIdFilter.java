package com.nowcoder.community.common.web;

import com.nowcoder.community.common.trace.TraceId;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_TRACEPARENT = "traceparent";
    public static final String MDC_KEY_TRACE_ID = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String traceId = req.getHeader(HEADER_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = extractTraceIdFromTraceparent(req.getHeader(HEADER_TRACEPARENT));
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceId.generate();
        }

        TraceId.set(traceId);
        MDC.put(MDC_KEY_TRACE_ID, traceId);
        resp.setHeader(HEADER_TRACE_ID, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY_TRACE_ID);
            TraceId.clear();
        }
    }

    private String extractTraceIdFromTraceparent(String traceparent) {
        // W3C Trace Context: version-traceid-spanid-flags
        // 示例：00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
        if (traceparent == null || traceparent.isBlank()) {
            return null;
        }
        String[] parts = traceparent.trim().split("-");
        if (parts.length != 4) {
            return null;
        }
        String traceId = parts[1];
        if (traceId == null || traceId.length() != 32) {
            return null;
        }
        for (int i = 0; i < traceId.length(); i++) {
            char c = traceId.charAt(i);
            boolean ok = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!ok) {
                return null;
            }
        }
        return traceId.toLowerCase();
    }
}
