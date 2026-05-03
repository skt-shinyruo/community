package com.nowcoder.community.im.gateway.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ImGatewayMetrics {

    private static final String UNKNOWN_REASON = "unknown";
    private static final Set<String> SESSION_FAILURE_REASONS = Set.of(
            "invalid_token",
            "no_workers",
            "unexpected"
    );
    private static final Set<String> BRIDGE_FAILURE_REASONS = Set.of(
            "internal_bridge_error",
            "unsupported_frame_type",
            "unsupported_worker_frame_type"
    );

    private final MeterRegistry registry;
    private final AtomicInteger activeConnections = new AtomicInteger();

    public ImGatewayMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("community.im.gateway.ws.active", activeConnections);
    }

    public void sessionOpened() {
        registry.counter("community.im.gateway.session.opened").increment();
    }

    public void sessionFailed(String reason) {
        registry.counter("community.im.gateway.session.failed", "reason", knownReason(reason, SESSION_FAILURE_REASONS))
                .increment();
    }

    public void bridgeOpened() {
        registry.counter("community.im.gateway.bridge.opened").increment();
    }

    public void bridgeFailed(String reason) {
        registry.counter("community.im.gateway.bridge.failed", "reason", knownReason(reason, BRIDGE_FAILURE_REASONS))
                .increment();
    }

    public void invalidFirstFrame() {
        registry.counter("community.im.gateway.ws.invalid_first_frame").increment();
    }

    public void invalidTicket() {
        registry.counter("community.im.gateway.ticket.invalid").increment();
    }

    public void workerUnavailable() {
        registry.counter("community.im.gateway.worker.unavailable").increment();
    }

    public void connectionOpened() {
        activeConnections.incrementAndGet();
    }

    public void connectionClosed() {
        activeConnections.updateAndGet(value -> Math.max(0, value - 1));
    }

    private static String knownReason(String reason, Set<String> allowedReasons) {
        if (reason == null || reason.isBlank()) {
            return UNKNOWN_REASON;
        }
        String value = reason.trim();
        return allowedReasons.contains(value) ? value : UNKNOWN_REASON;
    }
}
