package com.nowcoder.community.im.realtime.fanout;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RoomPersistedOwnerConsumerTest {

    @Test
    void nullEventStillRecordsConsumptionAndEntersOwnerService() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RoomFanoutOwnerService ownerService = mock(RoomFanoutOwnerService.class);
        RoomPersistedOwnerConsumer consumer = new RoomPersistedOwnerConsumer(
                ownerService,
                new RoomFanoutMetrics(meterRegistry)
        );

        consumer.onRoomPersisted(null);

        assertThat(meterRegistry.get("im_room_fanout_events_consumed")
                .tag("path", "owner")
                .counter()
                .count()).isEqualTo(1.0);
        verify(ownerService).routeAndDispatch(null);
    }
}
