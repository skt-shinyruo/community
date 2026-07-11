package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.common.command.RoomFanoutCommand;
import com.nowcoder.community.im.realtime.push.RoomFanoutCoalescer;
import com.nowcoder.community.im.realtime.session.ImSessionProperties;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RoomFanoutTargetServiceTest {

    private final RoomFanoutCoalescer roomFanoutCoalescer = mock(RoomFanoutCoalescer.class);
    private final ImSessionProperties sessionProperties = new ImSessionProperties();

    RoomFanoutTargetServiceTest() {
        sessionProperties.setWorkerId("worker-a");
    }

    @Test
    void appliesCommandOnlyWhenTargetWorkerMatchesLocalWorker() {
        UUID roomId = uuid(1);
        RoomFanoutCommand command = new RoomFanoutCommand("worker-a", roomId, 42L, "evt-1", 1000L);

        RoomFanoutTargetResult result = service().apply(command);

        assertThat(result).isEqualTo(RoomFanoutTargetResult.ACCEPTED);
        verify(roomFanoutCoalescer).markRoomUpdated(roomId, 42L);
    }

    @Test
    void rejectsCommandForAnotherWorkerWithoutLocalFanout() {
        UUID roomId = uuid(2);
        RoomFanoutCommand command = new RoomFanoutCommand("worker-b", roomId, 43L, "evt-2", 1000L);

        RoomFanoutTargetResult result = service().apply(command);

        assertThat(result).isEqualTo(RoomFanoutTargetResult.WRONG_TARGET);
        verifyNoInteractions(roomFanoutCoalescer);
    }

    @Test
    void rejectsInvalidCommandWithoutLocalFanout() {
        RoomFanoutCommand command = new RoomFanoutCommand("worker-a", uuid(3), 0L, "evt-3", 1000L);

        RoomFanoutTargetResult result = service().apply(command);

        assertThat(result).isEqualTo(RoomFanoutTargetResult.INVALID);
        verifyNoInteractions(roomFanoutCoalescer);
    }

    @Test
    void duplicateSourceEventIdDoesNotTriggerLocalFanoutAgain() {
        UUID roomId = uuid(4);
        RoomFanoutTargetService service = service();
        RoomFanoutCommand command = new RoomFanoutCommand("worker-a", roomId, 44L, "evt-duplicate", 1000L);

        RoomFanoutTargetResult first = service.apply(command);
        RoomFanoutTargetResult duplicate = service.apply(command);

        assertThat(first).isEqualTo(RoomFanoutTargetResult.ACCEPTED);
        assertThat(duplicate.name()).isEqualTo("DUPLICATE");
        verify(roomFanoutCoalescer, times(1)).markRoomUpdated(roomId, 44L);
    }

    @Test
    void failedLocalFanoutCanBeRetriedWithSameSourceEventId() {
        UUID roomId = uuid(5);
        RoomFanoutTargetService service = service();
        RoomFanoutCommand command = new RoomFanoutCommand("worker-a", roomId, 45L, "evt-retry", 1000L);
        doThrow(new IllegalStateException("enqueue failed"))
                .doNothing()
                .when(roomFanoutCoalescer)
                .markRoomUpdated(roomId, 45L);

        assertThatThrownBy(() -> service.apply(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("enqueue failed");

        RoomFanoutTargetResult retry = service.apply(command);

        assertThat(retry).isEqualTo(RoomFanoutTargetResult.ACCEPTED);
        verify(roomFanoutCoalescer, times(2)).markRoomUpdated(roomId, 45L);
    }

    @Test
    void sourceEventIdIsRequiredForIdempotentInternalFanout() {
        RoomFanoutCommand command = new RoomFanoutCommand("worker-a", uuid(5), 45L, " ", 1000L);

        RoomFanoutTargetResult result = service().apply(command);

        assertThat(result).isEqualTo(RoomFanoutTargetResult.INVALID);
        verifyNoInteractions(roomFanoutCoalescer);
    }

    private RoomFanoutTargetService service() {
        return new RoomFanoutTargetService(roomFanoutCoalescer, sessionProperties);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
