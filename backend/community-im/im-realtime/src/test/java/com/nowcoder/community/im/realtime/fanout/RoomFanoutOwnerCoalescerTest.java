package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import com.nowcoder.community.im.realtime.presence.RoomPresenceDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RoomFanoutOwnerCoalescerTest {

    private RoomFanoutOwnerCoalescer coalescer;

    @AfterEach
    void tearDown() {
        if (coalescer != null) {
            coalescer.destroy();
        }
    }

    @Test
    void flushRoutesLatestSeqOncePerActiveTargetWorker() {
        UUID roomId = uuid(1);
        RecordingPresenceDirectory presenceDirectory = new RecordingPresenceDirectory();
        presenceDirectory.activate(roomId, "worker-a");
        presenceDirectory.activate(roomId, "worker-b");
        presenceDirectory.activate(uuid(99), "idle-worker");
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        coalescer = newCoalescer(presenceDirectory, dispatcher);

        coalescer.markRoomUpdated(event("evt-1", roomId, 10L));
        coalescer.markRoomUpdated(event("evt-2", roomId, 11L));
        coalescer.flushOnce();

        assertThat(dispatcher.commands)
                .extracting(RoomFanoutCommand::targetWorkerId)
                .containsExactlyInAnyOrder("worker-a", "worker-b");
        assertThat(dispatcher.commands)
                .extracting(RoomFanoutCommand::lastSeq)
                .containsOnly(11L);
    }

    @Test
    void flushDoesNotDispatchWhenNoWorkerHasRoomPresence() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        coalescer = newCoalescer(new RecordingPresenceDirectory(), dispatcher);

        coalescer.markRoomUpdated(event("evt-3", uuid(2), 12L));
        coalescer.flushOnce();

        assertThat(dispatcher.commands).isEmpty();
    }

    @Test
    void shadowModeComputesRoutesWithoutDispatchingTargetCommands() {
        UUID roomId = uuid(4);
        RecordingPresenceDirectory presenceDirectory = new RecordingPresenceDirectory();
        presenceDirectory.activate(roomId, "worker-a");
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        RoomFanoutProperties properties = new RoomFanoutProperties();
        properties.setMode("shadow");
        properties.setOwnerFlushInterval(Duration.ofMinutes(10));
        coalescer = new RoomFanoutOwnerCoalescer(
                new RoomFanoutRoutingService(presenceDirectory, new RoomFanoutPlanner()),
                dispatcher,
                properties,
                RoomFanoutMetrics.noop()
        );

        coalescer.markRoomUpdated(event("evt-shadow", roomId, 13L));
        coalescer.flushOnce();

        assertThat(dispatcher.commands).isEmpty();
    }

    @Test
    void routeFailureDoesNotBlockRemainingTargetWorkers() {
        UUID roomId = uuid(5);
        RecordingPresenceDirectory presenceDirectory = new RecordingPresenceDirectory();
        presenceDirectory.activate(roomId, "worker-a");
        presenceDirectory.activate(roomId, "worker-b");
        FailingDispatcher dispatcher = new FailingDispatcher("worker-a");
        coalescer = newCoalescer(presenceDirectory, dispatcher);

        coalescer.markRoomUpdated(event("evt-failure", roomId, 14L));

        assertThatCode(() -> coalescer.flushOnce()).doesNotThrowAnyException();
        assertThat(dispatcher.attemptedWorkerIds).containsExactly("worker-a", "worker-b");
        assertThat(dispatcher.acceptedCommands)
                .extracting(RoomFanoutCommand::targetWorkerId)
                .containsExactly("worker-b");
    }

    @Test
    void dispatchFailureKeepsLatestRoomUpdatePendingForRetry() {
        UUID roomId = uuid(6);
        RecordingPresenceDirectory presenceDirectory = new RecordingPresenceDirectory();
        presenceDirectory.activate(roomId, "worker-a");
        FlakyDispatcher dispatcher = new FlakyDispatcher("worker-a");
        coalescer = newCoalescer(presenceDirectory, dispatcher);

        coalescer.markRoomUpdated(event("evt-retry", roomId, 15L));

        coalescer.flushOnce();
        coalescer.flushOnce();

        assertThat(dispatcher.attemptedWorkerIds).containsExactly("worker-a", "worker-a");
        assertThat(dispatcher.acceptedCommands)
                .extracting(RoomFanoutCommand::sourceEventId)
                .containsExactly("evt-retry");
    }

    @Test
    void routeFailureKeepsLatestRoomUpdatePendingForRetry() {
        UUID roomId = uuid(7);
        FailingOncePresenceDirectory presenceDirectory = new FailingOncePresenceDirectory();
        presenceDirectory.activate(roomId, "worker-a");
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        coalescer = newCoalescer(presenceDirectory, dispatcher);

        coalescer.markRoomUpdated(event("evt-route-retry", roomId, 16L));

        assertThatCode(() -> coalescer.flushOnce()).doesNotThrowAnyException();
        coalescer.flushOnce();

        assertThat(dispatcher.commands)
                .extracting(RoomFanoutCommand::sourceEventId)
                .containsExactly("evt-route-retry");
    }

    private RoomFanoutOwnerCoalescer newCoalescer(
            RoomPresenceDirectory presenceDirectory,
            RoomFanoutDispatcher dispatcher
    ) {
        RoomFanoutProperties properties = new RoomFanoutProperties();
        properties.setOwnerFlushInterval(Duration.ofMinutes(10));
        return new RoomFanoutOwnerCoalescer(
                new RoomFanoutRoutingService(presenceDirectory, new RoomFanoutPlanner()),
                dispatcher,
                properties,
                RoomFanoutMetrics.noop()
        );
    }

    private static RoomMessagePersistedEvent event(String eventId, UUID roomId, long seq) {
        return new RoomMessagePersistedEvent(eventId, roomId, seq, uuid(seq), uuid(500), 1000L + seq);
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
                throw new IllegalStateException("planned failure");
            }
            acceptedCommands.add(command);
        }
    }

    private static final class FlakyDispatcher implements RoomFanoutDispatcher {
        private final String failingWorkerId;
        private final List<String> attemptedWorkerIds = new ArrayList<>();
        private final List<RoomFanoutCommand> acceptedCommands = new ArrayList<>();
        private boolean failed;

        private FlakyDispatcher(String failingWorkerId) {
            this.failingWorkerId = failingWorkerId;
        }

        @Override
        public void dispatch(RoomFanoutCommand command) {
            attemptedWorkerIds.add(command.targetWorkerId());
            if (!failed && failingWorkerId.equals(command.targetWorkerId())) {
                failed = true;
                throw new IllegalStateException("planned one-shot failure");
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

    private static final class FailingOncePresenceDirectory extends RecordingPresenceDirectory {
        private boolean failed;

        @Override
        public Set<String> activeWorkerIds(UUID roomId) {
            if (!failed) {
                failed = true;
                throw new IllegalStateException("planned route failure");
            }
            return super.activeWorkerIds(roomId);
        }
    }
}
