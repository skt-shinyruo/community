package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.common.command.RoomFanoutCommand;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoomFanoutTargetConsumerTest {

    @Test
    void acceptedCommandCommitsTargetInboxOffsetAndRecordsMetric() {
        RoomFanoutTargetService targetService = mock(RoomFanoutTargetService.class);
        RoomFanoutCommand command = command("evt-1");
        when(targetService.apply(command)).thenReturn(RoomFanoutTargetResult.ACCEPTED);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RoomFanoutTargetConsumer consumer = new RoomFanoutTargetConsumer(
                targetService,
                new RoomFanoutMetrics(meterRegistry)
        );

        assertThatCode(() -> consumer.onCommand(command)).doesNotThrowAnyException();

        assertTargetResultCount(meterRegistry, "accepted", 1.0);
    }

    @Test
    void duplicateCommandCommitsTargetInboxOffsetAndRecordsMetric() {
        RoomFanoutTargetService targetService = mock(RoomFanoutTargetService.class);
        RoomFanoutCommand command = command("evt-duplicate");
        when(targetService.apply(command)).thenReturn(RoomFanoutTargetResult.DUPLICATE);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RoomFanoutTargetConsumer consumer = new RoomFanoutTargetConsumer(
                targetService,
                new RoomFanoutMetrics(meterRegistry)
        );

        assertThatCode(() -> consumer.onCommand(command)).doesNotThrowAnyException();

        assertTargetResultCount(meterRegistry, "duplicate", 1.0);
    }

    @Test
    void rejectedTargetCommandRecordsMetricThenThrowsForKafkaRetryOrDlq() {
        RoomFanoutTargetService targetService = mock(RoomFanoutTargetService.class);
        RoomFanoutCommand command = command("evt-2");
        when(targetService.apply(command)).thenReturn(RoomFanoutTargetResult.WRONG_TARGET);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RoomFanoutTargetConsumer consumer = new RoomFanoutTargetConsumer(
                targetService,
                new RoomFanoutMetrics(meterRegistry)
        );

        assertThatThrownBy(() -> consumer.onCommand(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("room fanout target command rejected");

        assertTargetResultCount(meterRegistry, "rejected", 1.0);
    }

    private static void assertTargetResultCount(
            SimpleMeterRegistry meterRegistry,
            String result,
            double expectedCount
    ) {
        assertThat(meterRegistry.get("im_room_fanout_target_results")
                .tag("result", result)
                .counter()
                .count()).isEqualTo(expectedCount);
    }

    private static RoomFanoutCommand command(String sourceEventId) {
        return new RoomFanoutCommand(
                "worker-a",
                UUID.fromString("00000000-0000-7000-8000-000000000001"),
                42L,
                sourceEventId,
                1000L
        );
    }
}
