package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.common.command.RoomFanoutCommand;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import com.nowcoder.community.im.realtime.presence.RoomPresenceDirectory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomFanoutOwnerServiceTest {

    @Test
    void noActiveWorkerIsSuccessfulNoop() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        RoomFanoutOwnerService ownerService = ownerService(new RecordingPresenceDirectory(), dispatcher);

        assertThatCode(() -> ownerService.routeAndDispatch(event("evt-empty", uuid(1), 10L, 1010L)))
                .doesNotThrowAnyException();

        assertThat(dispatcher.commands).isEmpty();
    }

    @Test
    void dispatchesOneCommandToEachActiveWorker() {
        UUID roomId = uuid(2);
        RecordingPresenceDirectory presenceDirectory = new RecordingPresenceDirectory();
        presenceDirectory.activate(roomId, "worker-a");
        presenceDirectory.activate(roomId, "worker-b");
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        RoomFanoutOwnerService ownerService = ownerService(presenceDirectory, dispatcher);

        ownerService.routeAndDispatch(event("evt-two", roomId, 11L, 1011L));

        assertThat(dispatcher.commands)
                .extracting(RoomFanoutCommand::targetWorkerId)
                .containsExactly("worker-a", "worker-b");
        assertThat(dispatcher.commands)
                .extracting(RoomFanoutCommand::roomId, RoomFanoutCommand::lastSeq)
                .containsOnly(org.assertj.core.groups.Tuple.tuple(roomId, 11L));
    }

    @Test
    void firstDispatchFailureStillAttemptsRemainingWorkersThenEscapes() {
        UUID roomId = uuid(3);
        RecordingPresenceDirectory presenceDirectory = new RecordingPresenceDirectory();
        presenceDirectory.activate(roomId, "worker-a");
        presenceDirectory.activate(roomId, "worker-b");
        FailingDispatcher dispatcher = new FailingDispatcher("worker-a");
        RoomFanoutOwnerService ownerService = ownerService(presenceDirectory, dispatcher);

        assertThatThrownBy(() -> ownerService.routeAndDispatch(event("evt-failure", roomId, 12L, 1012L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("room fanout routed dispatch failed")
                .hasCauseInstanceOf(IllegalStateException.class);

        assertThat(dispatcher.attemptedWorkerIds).containsExactly("worker-a", "worker-b");
        assertThat(dispatcher.acceptedCommands)
                .extracting(RoomFanoutCommand::targetWorkerId)
                .containsExactly("worker-b");
    }

    @Test
    void directoryFailureEscapesWithoutDispatching() {
        IllegalStateException directoryFailure = new IllegalStateException("planned directory failure");
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        RoomFanoutOwnerService ownerService = ownerService(
                new FailingPresenceDirectory(directoryFailure),
                dispatcher
        );

        assertThatThrownBy(() -> ownerService.routeAndDispatch(event("evt-directory", uuid(4), 13L, 1013L)))
                .isSameAs(directoryFailure);
        assertThat(dispatcher.commands).isEmpty();
    }

    @Test
    void copiesSourceEventIdAndOriginalCreatedTimeToEveryCommand() {
        UUID roomId = uuid(5);
        RecordingPresenceDirectory presenceDirectory = new RecordingPresenceDirectory();
        presenceDirectory.activate(roomId, "worker-a");
        presenceDirectory.activate(roomId, "worker-b");
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        RoomFanoutOwnerService ownerService = ownerService(presenceDirectory, dispatcher);

        ownerService.routeAndDispatch(event("evt-stable", roomId, 14L, 998877L));

        assertThat(dispatcher.commands)
                .extracting(RoomFanoutCommand::sourceEventId)
                .containsOnly("evt-stable");
        assertThat(dispatcher.commands)
                .extracting(RoomFanoutCommand::createdAtEpochMs)
                .containsOnly(998877L);
    }

    private static RoomFanoutOwnerService ownerService(
            RoomPresenceDirectory presenceDirectory,
            RoomFanoutDispatcher dispatcher
    ) {
        return new RoomFanoutOwnerService(
                new RoomFanoutRoutingService(presenceDirectory, new RoomFanoutPlanner()),
                dispatcher,
                RoomFanoutMetrics.noop()
        );
    }

    private static RoomMessagePersistedEvent event(
            String eventId,
            UUID roomId,
            long seq,
            long createdAtEpochMs
    ) {
        return new RoomMessagePersistedEvent(eventId, roomId, seq, uuid(seq), uuid(500), createdAtEpochMs);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static final class RecordingDispatcher implements RoomFanoutDispatcher {
        private final List<RoomFanoutCommand> commands = new ArrayList<>();

        @Override
        public void dispatch(RoomFanoutCommand command) {
            commands.add(command);
        }
    }

    private static final class FailingDispatcher implements RoomFanoutDispatcher {
        private final String failingWorkerId;
        private final List<String> attemptedWorkerIds = new ArrayList<>();
        private final List<RoomFanoutCommand> acceptedCommands = new ArrayList<>();

        private FailingDispatcher(String failingWorkerId) {
            this.failingWorkerId = failingWorkerId;
        }

        @Override
        public void dispatch(RoomFanoutCommand command) {
            attemptedWorkerIds.add(command.targetWorkerId());
            if (failingWorkerId.equals(command.targetWorkerId())) {
                throw new IllegalStateException("planned dispatch failure");
            }
            acceptedCommands.add(command);
        }
    }

    private static class RecordingPresenceDirectory implements RoomPresenceDirectory {
        private final Map<UUID, Set<String>> workerIdsByRoomId = new LinkedHashMap<>();

        @Override
        public void activate(UUID roomId, String workerId) {
            workerIdsByRoomId.computeIfAbsent(roomId, ignored -> new LinkedHashSet<>()).add(workerId);
        }

        @Override
        public void deactivate(UUID roomId, String workerId) {
            Set<String> workerIds = workerIdsByRoomId.get(roomId);
            if (workerIds != null) {
                workerIds.remove(workerId);
            }
        }

        @Override
        public Set<String> activeWorkerIds(UUID roomId) {
            Set<String> workerIds = workerIdsByRoomId.get(roomId);
            return workerIds == null ? Set.of() : new LinkedHashSet<>(workerIds);
        }
    }

    private static final class FailingPresenceDirectory implements RoomPresenceDirectory {
        private final RuntimeException failure;

        private FailingPresenceDirectory(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void activate(UUID roomId, String workerId) {
        }

        @Override
        public void deactivate(UUID roomId, String workerId) {
        }

        @Override
        public Set<String> activeWorkerIds(UUID roomId) {
            throw failure;
        }
    }
}
