package com.nowcoder.community.im.realtime.presence;

import com.nowcoder.community.im.realtime.session.ConnectionSession;
import com.nowcoder.community.im.realtime.session.InMemoryConnectionOutput;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionRegistryMetricsTest {

    @Test
    void shouldExposeGaugesAndSupportNoAllocationIteration() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ConnectionRegistry registry = new ConnectionRegistry(meterRegistry);
        UUID userId1 = uuid(1);
        UUID userId2 = uuid(2);

        ConnectionSession c1 = connection("c1", userId1);
        registry.register(c1);

        assertThat(meterRegistry.get("im_ws_online_connections").gauge().value()).isEqualTo(1.0);
        assertThat(meterRegistry.get("im_ws_online_users").gauge().value()).isEqualTo(1.0);

        ConnectionSession c2 = connection("c2", userId1);
        registry.register(c2);

        ConnectionSession c3 = connection("c3", userId2);
        registry.register(c3);

        assertThat(meterRegistry.get("im_ws_online_connections").gauge().value()).isEqualTo(3.0);
        assertThat(meterRegistry.get("im_ws_online_users").gauge().value()).isEqualTo(2.0);

        List<String> user1ConnectionIds = new ArrayList<>();
        registry.forEachConnectionByUserId(userId1, conn -> user1ConnectionIds.add(conn.connectionId()));
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

    private static ConnectionSession connection(String connectionId, UUID userId) {
        ConnectionSession connection = new ConnectionSession(connectionId, new InMemoryConnectionOutput());
        connection.bindUser(userId);
        return connection;
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
