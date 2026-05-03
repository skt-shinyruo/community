package com.nowcoder.community.im.gateway.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImGatewayMetricsTest {

    @Test
    void shouldRecordSessionBridgeAndRoutingMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ImGatewayMetrics metrics = new ImGatewayMetrics(registry);

        metrics.sessionOpened();
        metrics.sessionFailed("invalid_token");
        metrics.bridgeOpened();
        metrics.bridgeFailed("internal_bridge_error");
        metrics.invalidFirstFrame();
        metrics.invalidTicket();
        metrics.workerUnavailable();
        metrics.connectionOpened();
        metrics.connectionClosed();

        assertThat(registry.counter("community.im.gateway.session.opened").count()).isEqualTo(1.0);
        assertThat(registry.counter("community.im.gateway.session.failed", "reason", "invalid_token").count())
                .isEqualTo(1.0);
        assertThat(registry.counter("community.im.gateway.bridge.opened").count()).isEqualTo(1.0);
        assertThat(registry.counter("community.im.gateway.bridge.failed", "reason", "internal_bridge_error").count())
                .isEqualTo(1.0);
        assertThat(registry.counter("community.im.gateway.ws.invalid_first_frame").count()).isEqualTo(1.0);
        assertThat(registry.counter("community.im.gateway.ticket.invalid").count()).isEqualTo(1.0);
        assertThat(registry.counter("community.im.gateway.worker.unavailable").count()).isEqualTo(1.0);
        assertThat(registry.get("community.im.gateway.ws.active").gauge().value()).isEqualTo(0.0);
    }

    @Test
    void shouldWhitelistReasonTagsAndKeepActiveGaugeNonNegative() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ImGatewayMetrics metrics = new ImGatewayMetrics(registry);

        metrics.sessionFailed(" bad reason:/token ");
        metrics.sessionFailed("no_workers");
        metrics.bridgeFailed("worker_unavailable");
        metrics.bridgeFailed("x".repeat(128));
        metrics.connectionClosed();
        metrics.connectionOpened();
        metrics.connectionOpened();
        metrics.connectionClosed();

        assertThat(registry.counter("community.im.gateway.session.failed", "reason", "unknown").count())
                .isEqualTo(1.0);
        assertThat(registry.counter("community.im.gateway.session.failed", "reason", "no_workers").count())
                .isEqualTo(1.0);
        assertThat(registry.counter("community.im.gateway.bridge.failed", "reason", "unknown").count())
                .isEqualTo(2.0);
        assertThat(registry.find("community.im.gateway.session.failed")
                .tag("reason", "bad_reason__token")
                .counter()).isNull();
        assertThat(registry.get("community.im.gateway.ws.active").gauge().value()).isEqualTo(1.0);
    }
}
