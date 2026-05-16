package com.nowcoder.community.common.web;

import com.nowcoder.community.common.trace.OtelTraceContext;
import com.nowcoder.community.common.trace.TraceContextScope;
import com.nowcoder.community.common.trace.TraceContextSnapshot;
import com.nowcoder.community.common.trace.TraceHeaders;
import io.opentelemetry.api.trace.SpanKind;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String method = req.getMethod() == null ? "request" : req.getMethod().toLowerCase();
        try (TraceContextScope ignored = OtelTraceContext.openForInbound(
                req.getHeader(TraceHeaders.HEADER_TRACEPARENT),
                "http " + method,
                SpanKind.SERVER
        )) {
            TraceContextSnapshot snapshot = TraceContextSnapshot.currentOrNew();
            resp.setHeader(TraceHeaders.HEADER_TRACEPARENT, snapshot.traceparent());
            chain.doFilter(request, response);
        }
    }
}
