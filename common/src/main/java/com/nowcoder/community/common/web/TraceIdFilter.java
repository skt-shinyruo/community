package com.nowcoder.community.common.web;

import com.nowcoder.community.common.trace.TraceId;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@ConditionalOnClass(Filter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TraceIdFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpServletRequest) || !(response instanceof HttpServletResponse httpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        String traceId = httpServletRequest.getHeader(TraceId.HEADER_NAME);
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceId.generate();
        }

        TraceId.put(traceId);
        httpServletResponse.setHeader(TraceId.HEADER_NAME, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            TraceId.clear();
        }
    }
}

