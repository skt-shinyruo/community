package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.common.command.RoomFanoutCommand;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoomFanoutTargetConsumerTest {

    @Test
    void acceptedAndDuplicateCommandsCommitTargetInboxOffset() {
        RoomFanoutTargetService targetService = mock(RoomFanoutTargetService.class);
        RoomFanoutCommand command = command("evt-1");
        when(targetService.apply(command))
                .thenReturn(RoomFanoutTargetResult.ACCEPTED)
                .thenReturn(RoomFanoutTargetResult.DUPLICATE);
        RoomFanoutTargetConsumer consumer = new RoomFanoutTargetConsumer(targetService);

        assertThatCode(() -> consumer.onCommand(command)).doesNotThrowAnyException();
        assertThatCode(() -> consumer.onCommand(command)).doesNotThrowAnyException();
    }

    @Test
    void rejectedTargetCommandThrowsForKafkaRetryOrDlq() {
        RoomFanoutTargetService targetService = mock(RoomFanoutTargetService.class);
        RoomFanoutCommand command = command("evt-2");
        when(targetService.apply(command)).thenReturn(RoomFanoutTargetResult.WRONG_TARGET);
        RoomFanoutTargetConsumer consumer = new RoomFanoutTargetConsumer(targetService);

        assertThatThrownBy(() -> consumer.onCommand(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("room fanout target command rejected");
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
