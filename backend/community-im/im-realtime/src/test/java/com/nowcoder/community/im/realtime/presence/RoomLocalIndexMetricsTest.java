package com.nowcoder.community.im.realtime.presence;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoomLocalIndexMetricsTest {

    @Test
    void shouldExposeRoomIndexMetricsWithSampling() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RoomLocalIndex index = new RoomLocalIndex(meterRegistry, 1);
        UUID roomId10 = uuid(10);
        UUID roomId11 = uuid(11);

        index.add(roomId10, "c1");
        index.add(roomId10, "c2");
        index.add(roomId11, "c3");

        assertThat(meterRegistry.get("im_ws_rooms_indexed").gauge().value()).isEqualTo(2.0);

        DistributionSummary summary = meterRegistry.get("im_ws_connections_per_room").summary();
        assertThat(summary.count()).isGreaterThanOrEqualTo(1);
        assertThat(summary.max()).isGreaterThanOrEqualTo(2.0);

        index.remove(roomId10, "c1");
        index.remove(roomId10, "c2");
        index.remove(roomId11, "c3");

        assertThat(meterRegistry.get("im_ws_rooms_indexed").gauge().value()).isEqualTo(0.0);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
