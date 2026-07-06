package com.nowcoder.community.analytics.infrastructure.web;

import com.nowcoder.community.analytics.application.AnalyticsRequestCaptureApplicationService;
import com.nowcoder.community.analytics.application.command.RecordRequestCommand;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class AnalyticsRequestCaptureFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsRequestCaptureFilter.class);
    private final AtomicLong captureFailureCount = new AtomicLong();

    private final AnalyticsRequestClassifier classifier;
    private final ClientIpResolver clientIpResolver;
    private final AnalyticsPrincipalResolver principalResolver;
    private final AnalyticsIngestProperties properties;
    private final AnalyticsRequestCaptureApplicationService analyticsRequestCaptureApplicationService;

    public AnalyticsRequestCaptureFilter(
            AnalyticsRequestClassifier classifier,
            ClientIpResolver clientIpResolver,
            AnalyticsPrincipalResolver principalResolver,
            AnalyticsIngestProperties properties,
            AnalyticsRequestCaptureApplicationService analyticsRequestCaptureApplicationService
    ) {
        this.classifier = classifier;
        this.clientIpResolver = clientIpResolver;
        this.principalResolver = principalResolver;
        this.properties = properties;
        this.analyticsRequestCaptureApplicationService = analyticsRequestCaptureApplicationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean chainCompleted = false;
        try {
            filterChain.doFilter(request, response);
            chainCompleted = true;
        } finally {
            if (chainCompleted) {
                recordSafely(request, response);
            }
        }
    }

    private void recordSafely(HttpServletRequest request, HttpServletResponse response) {
        try {
            recordIfEligible(request, response);
        } catch (RuntimeException e) {
            logCaptureFailure(request, response, e);
        }
    }

    private void logCaptureFailure(HttpServletRequest request, HttpServletResponse response, RuntimeException e) {
        long count = captureFailureCount.incrementAndGet();
        if (count <= 3 || (count & (count - 1)) == 0) {
            log.warn("[analytics][ingest] request capture failed: method={}, status={}, count={}, error={}",
                    request == null ? null : request.getMethod(),
                    response == null ? null : response.getStatus(),
                    count,
                    e.toString());
        }
        if (log.isDebugEnabled()) {
            log.debug("[analytics][ingest] request capture failed with stack trace", e);
        }
    }

    private void recordIfEligible(HttpServletRequest request, HttpServletResponse response) {
        AnalyticsRequestClassifier.Decision decision = classifier.classify(
                request == null ? null : request.getMethod(),
                request == null ? null : request.getRequestURI(),
                response == null ? 0 : response.getStatus()
        );
        if (decision == null || !decision.capture()) {
            return;
        }
        ClientIpResolver.ResolvedClientIp resolved = clientIpResolver.resolve(request);
        String ip = resolved == null ? null : resolved.ip();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = principalResolver.resolveUserUuid(authentication);
        boolean recordUv = properties != null && properties.isRecordUv();
        boolean recordDau = properties != null && properties.isRecordDau();
        analyticsRequestCaptureApplicationService.capture(new RecordRequestCommand(
                ip,
                userId,
                recordUv,
                recordDau
        ), properties != null && properties.isAsyncEnabled());
    }
}
