package com.nowcoder.community.common.web;

import com.nowcoder.community.common.logging.EventLogFields;
import com.nowcoder.community.common.trace.TraceId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuditLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuditLogFilter.class);
    private static final String CATEGORY = "audit";
    private static final String ACTION = "http_write_request";
    private static final String MDC_CATEGORY = EventLogFields.EVENT_CATEGORY;
    private static final String MDC_ACTION = EventLogFields.EVENT_ACTION;
    private static final String MDC_OUTCOME = EventLogFields.EVENT_OUTCOME;

    private final String appName;

    public AuditLogFilter(@Value("${spring.application.name:unknown}") String appName) {
        this.appName = appName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (path == null || method == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!path.startsWith("/api/") && !path.startsWith("/internal/")) {
            filterChain.doFilter(request, response);
            return;
        }
        if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }
        // 避免记录敏感登录参数（即便不记录 body，也尽量不污染审计流量）
        if (path.startsWith("/api/auth/login")) {
            filterChain.doFilter(request, response);
            return;
        }

        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long costMs = (System.nanoTime() - startNanos) / 1_000_000L;
            int status = response.getStatus();
            String userId = resolveUserId();
            String traceId = TraceId.get();
            infoEvent(
                    resolveOutcome(status),
                    method,
                    path,
                    status,
                    userId,
                    traceId,
                    costMs
            );
        }
    }

    private String resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "-";
        }
        String name = authentication.getName();
        if (!StringUtils.hasText(name) || "anonymousUser".equalsIgnoreCase(name)) {
            return "-";
        }
        return name;
    }

    private void infoEvent(String outcome, String method, String path, int status, String userId, String traceId, long costMs) {
        String previousCategory = MDC.get(MDC_CATEGORY);
        String previousAction = MDC.get(MDC_ACTION);
        String previousOutcome = MDC.get(MDC_OUTCOME);
        MDC.put(MDC_CATEGORY, CATEGORY);
        MDC.put(MDC_ACTION, ACTION);
        MDC.put(MDC_OUTCOME, outcome);
        try {
            log.info(
                    "[audit][app={}] method={} path={} status={} userId={} traceId={} costMs={}",
                    appName, method, path, status, userId, traceId, costMs
            );
        } finally {
            restore(MDC_CATEGORY, previousCategory);
            restore(MDC_ACTION, previousAction);
            restore(MDC_OUTCOME, previousOutcome);
        }
    }

    private String resolveOutcome(int status) {
        if (status >= 200 && status < 400) {
            return "success";
        }
        if (status == 401 || status == 403) {
            return "denied";
        }
        return "failure";
    }

    private void restore(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, previousValue);
    }
}
