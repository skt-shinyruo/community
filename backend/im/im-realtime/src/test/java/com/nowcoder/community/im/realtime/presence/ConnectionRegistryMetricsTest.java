package com.nowcoder.community.im.realtime.presence;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionRegistryMetricsTest {

    @Test
    void shouldExposeGaugesAndSupportNoAllocationIteration() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ConnectionRegistry registry = new ConnectionRegistry(meterRegistry);

        WebSocketSession session1 = Mockito.mock(WebSocketSession.class);
        Mockito.when(session1.close()).thenReturn(Mono.empty());
        WsConnection c1 = new WsConnection("c1", session1, 10);
        c1.bindUser(1);
        registry.register(c1);

        assertThat(meterRegistry.get("im_ws_online_connections").gauge().value()).isEqualTo(1.0);
        assertThat(meterRegistry.get("im_ws_online_users").gauge().value()).isEqualTo(1.0);

        WebSocketSession session2 = Mockito.mock(WebSocketSession.class);
        Mockito.when(session2.close()).thenReturn(Mono.empty());
        WsConnection c2 = new WsConnection("c2", session2, 10);
        c2.bindUser(1);
        registry.register(c2);

        WebSocketSession session3 = Mockito.mock(WebSocketSession.class);
        Mockito.when(session3.close()).thenReturn(Mono.empty());
        WsConnection c3 = new WsConnection("c3", session3, 10);
        c3.bindUser(2);
        registry.register(c3);

        assertThat(meterRegistry.get("im_ws_online_connections").gauge().value()).isEqualTo(3.0);
        assertThat(meterRegistry.get("im_ws_online_users").gauge().value()).isEqualTo(2.0);

        List<String> user1ConnectionIds = new ArrayList<>();
        registry.forEachConnectionByUserId(1, conn -> user1ConnectionIds.add(conn.connectionId()));
        assertThat(user1ConnectionIds).containsExactlyInAnyOrder("c1", "c2");

        DistributionSummary summary = meterRegistry.get("im_ws_connections_per_user").summary();
        assertThat(summary.count()).isGreaterThanOrEqualTo(1);
        assertThat(summary.max()).isGreaterThanOrEqualTo(2.0);

        registry.unregister(c1);
        registry.unregister(c2);
        registry.unregister(c3);

        assertThat(meterRegistry.get("im_ws_online_connections").gauge().value()).isEqualTo(0.0);
        assertThat(meterRegistry.get("im_ws_online_users").gauge().value()).isEqualTo(0.0);
    }
}

