package com.nowcoder.community.im.realtime.fanout;

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

class RoomFanoutRoutingServiceTest {

    @Test
    void routesOncePerActiveWorkerAndIgnoresDuplicatePresence() {
        UUID roomId = uuid(1);
        RecordingPresenceDirectory directory = new RecordingPresenceDirectory();
        directory.activate(roomId, "worker-a");
        directory.activate(roomId, " worker-a ");
        directory.activate(roomId, "");
        directory.activate(roomId, "worker-b");

        RoomFanoutRoutingService service = new RoomFanoutRoutingService(directory, new RoomFanoutPlanner());

        List<RoomFanoutRoute> routes = service.routesFor(roomId, 42L);

        assertThat(routes)
                .extracting(RoomFanoutRoute::targetWorkerId)
                .containsExactly("worker-a", "worker-b");
        assertThat(routes)
                .allSatisfy(route -> {
                    assertThat(route.roomId()).isEqualTo(roomId);
                    assertThat(route.lastSeq()).isEqualTo(42L);
                });
    }

    @Test
    void routeCountIsBoundedByWorkersPresentInRoomNotTotalWorkers() {
        UUID hotRoomId = uuid(10);
        UUID unrelatedRoomId = uuid(11);
        RecordingPresenceDirectory directory = new RecordingPresenceDirectory();
        directory.activate(hotRoomId, "worker-a");
        directory.activate(hotRoomId, "worker-b");
        for (int i = 0; i < 100; i++) {
            directory.activate(unrelatedRoomId, "worker-extra-" + i);
        }

        RoomFanoutRoutingService service = new RoomFanoutRoutingService(directory, new RoomFanoutPlanner());

        List<RoomFanoutRoute> routes = service.routesFor(hotRoomId, 100L);

        assertThat(routes)
                .extracting(RoomFanoutRoute::targetWorkerId)
                .containsExactly("worker-a", "worker-b");
    }

    @Test
    void invalidRoomOrSeqProducesNoRoutes() {
        RoomFanoutRoutingService service = new RoomFanoutRoutingService(
                new RecordingPresenceDirectory(),
                new RoomFanoutPlanner()
        );

        assertThat(service.routesFor(null, 1L)).isEmpty();
        assertThat(service.routesFor(uuid(1), 0L)).isEmpty();
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static final class RecordingPresenceDirectory implements RoomPresenceDirectory {

        private final Map<UUID, Set<String>> activeWorkersByRoomId = new LinkedHashMap<>();

        @Override
        public void activate(UUID roomId, String workerId) {
            activeWorkersByRoomId.computeIfAbsent(roomId, ignored -> new LinkedHashSet<>()).add(workerId);
        }

        @Override
        public void deactivate(UUID roomId, String workerId) {
            Set<String> workerIds = activeWorkersByRoomId.get(roomId);
            if (workerIds != null) {
                workerIds.remove(workerId);
            }
        }

        @Override
        public Set<String> activeWorkerIds(UUID roomId) {
            Set<String> workerIds = activeWorkersByRoomId.get(roomId);
            return workerIds == null ? Set.of() : new LinkedHashSet<>(workerIds);
        }
    }
}
