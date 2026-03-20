package com.nowcoder.community.im.realtime.presence;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoomLocalIndexMetricsTest {

    @Test
    void shouldExposeRoomIndexMetricsWithSampling() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RoomLocalIndex index = new RoomLocalIndex(meterRegistry, 1);

        index.add(10L, "c1");
        index.add(10L, "c2");
        index.add(11L, "c3");

        assertThat(meterRegistry.get("im_ws_rooms_indexed").gauge().value()).isEqualTo(2.0);

        DistributionSummary summary = meterRegistry.get("im_ws_connections_per_room").summary();
        assertThat(summary.count()).isGreaterThanOrEqualTo(1);
        assertThat(summary.max()).isGreaterThanOrEqualTo(2.0);

        index.remove(10L, "c1");
        index.remove(10L, "c2");
        index.remove(11L, "c3");

        assertThat(meterRegistry.get("im_ws_rooms_indexed").gauge().value()).isEqualTo(0.0);
    }
}

