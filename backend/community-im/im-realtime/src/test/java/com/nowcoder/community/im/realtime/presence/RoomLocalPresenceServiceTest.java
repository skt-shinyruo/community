package com.nowcoder.community.im.realtime.presence;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoomLocalPresenceServiceTest {

    @Test
    void activatesDistributedPresenceOnlyForFirstLocalConnection() {
        RecordingDirectory directory = new RecordingDirectory();
        RoomLocalPresenceService service = new RoomLocalPresenceService(new RoomLocalIndex(), directory, "worker-a");
        UUID roomId = uuid(1);

        service.addLocalConnection(roomId, "c1");
        service.addLocalConnection(roomId, "c2");

        assertThat(directory.activations).containsExactly("worker-a:" + roomId);
        assertThat(directory.activeWorkerIds(roomId)).containsExactly("worker-a");
    }

    @Test
    void deactivatesDistributedPresenceOnlyAfterLastLocalConnectionLeaves() {
        RecordingDirectory directory = new RecordingDirectory();
        RoomLocalPresenceService service = new RoomLocalPresenceService(new RoomLocalIndex(), directory, "worker-a");
        UUID roomId = uuid(1);
        service.addLocalConnection(roomId, "c1");
        service.addLocalConnection(roomId, "c2");

        service.removeLocalConnection(roomId, "c1");
        assertThat(directory.deactivations).isEmpty();
        assertThat(directory.activeWorkerIds(roomId)).containsExactly("worker-a");

        service.removeLocalConnection(roomId, "c2");

        assertThat(directory.deactivations).containsExactly("worker-a:" + roomId);
        assertThat(directory.activeWorkerIds(roomId)).isEmpty();
    }

    @Test
    void keepsRoomLocalIndexInSync() {
        RecordingDirectory directory = new RecordingDirectory();
        RoomLocalIndex roomLocalIndex = new RoomLocalIndex();
        RoomLocalPresenceService service = new RoomLocalPresenceService(roomLocalIndex, directory, "worker-a");
        UUID roomId = uuid(1);

        service.addLocalConnection(roomId, "c1");
        service.addLocalConnection(roomId, "c2");
        service.removeLocalConnection(roomId, "c1");

        ArrayList<String> connectionIds = new ArrayList<>();
        roomLocalIndex.forEachConnectionId(roomId, connectionIds::add);

        assertThat(connectionIds).containsExactly("c2");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static final class RecordingDirectory implements RoomPresenceDirectory {

        private final List<String> activations = new ArrayList<>();
        private final List<String> deactivations = new ArrayList<>();
        private final ArrayList<String> active = new ArrayList<>();

        @Override
        public void activate(UUID roomId, String workerId) {
            String key = key(workerId, roomId);
            activations.add(key);
            if (!active.contains(key)) {
                active.add(key);
            }
        }

        @Override
        public void deactivate(UUID roomId, String workerId) {
            String key = key(workerId, roomId);
            deactivations.add(key);
            active.remove(key);
        }

        @Override
        public Set<String> activeWorkerIds(UUID roomId) {
            ArrayList<String> workerIds = new ArrayList<>();
            for (String key : active) {
                if (key.endsWith(":" + roomId)) {
                    workerIds.add(key.substring(0, key.indexOf(':')));
                }
            }
            return Set.copyOf(workerIds);
        }

        private static String key(String workerId, UUID roomId) {
            return workerId + ":" + roomId;
        }
    }
}
