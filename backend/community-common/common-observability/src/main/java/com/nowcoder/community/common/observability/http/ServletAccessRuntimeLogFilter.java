package com.nowcoder.community.common.observability.http;

import com.nowcoder.community.common.observability.logging.RuntimeLogEvent;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ServletAccessRuntimeLogFilter extends OncePerRequestFilter {

    private final RuntimeLogWriter logWriter;
    private final RuntimeLoggingProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public ServletAccessRuntimeLogFilter(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        this.logWriter = logWriter;
        this.properties = properties;
    }

    public RuntimeLogWriter logWriter() {
        return logWriter;
    }

    public RuntimeLoggingProperties properties() {
        return properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isExcluded(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            logCompletedRequest(request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
        }
    }

    public boolean logCompletedRequest(String method, String rawPath, int status, long durationMs) {
        long thresholdMs = properties.getHttp().getSlowRequestThresholdMs();
        if (durationMs < thresholdMs) {
            return false;
        }
        String path = sanitizePath(rawPath);
        if (isExcluded(path)) {
            return false;
        }
        logWriter.warn(RuntimeLogEvent.builder("access", "http_slow_request", resolveOutcome(status), "http slow request")
                .field("http.request.method", method)
                .field("url.path", path)
                .field("http.response.status_code", status)
                .field(RuntimeLogFields.DURATION_MS, durationMs)
                .field(RuntimeLogFields.THRESHOLD_MS, thresholdMs)
                .build());
        return true;
    }

    private boolean isExcluded(String path) {
        String sanitizedPath = sanitizePath(path);
        for (String pattern : properties.getHttp().getExcludePaths()) {
            if (pathMatcher.match(pattern, sanitizedPath)) {
                return true;
            }
        }
        return false;
    }

    private String sanitizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "/";
        }
        int queryIndex = rawPath.indexOf('?');
        String path = queryIndex >= 0 ? rawPath.substring(0, queryIndex) : rawPath;
        int semicolonIndex = path.indexOf(';');
        if (semicolonIndex >= 0) {
            path = path.substring(0, semicolonIndex);
        }
        return path.isBlank() ? "/" : path;
    }

    private String resolveOutcome(int status) {
        if (status == 401 || status == 403) {
            return "denied";
        }
        if (status >= 200 && status < 400) {
            return "success";
        }
        return "failure";
    }
}
