package com.nowcoder.community.im.realtime.presence;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

class RoomLocalPresenceServiceTest {

    @Test
    void joinKeepsLocalMembershipWhenActivationFails() {
        RecordingDirectory directory = new RecordingDirectory();
        RoomLocalIndex index = new RoomLocalIndex();
        RoomLocalPresenceService service = service(index, directory);
        WsConnection connection = connection("c1");
        UUID roomId = uuid(1);
        directory.failNextActivation(roomId);

        assertThatCode(() -> service.joinLocalRoom(roomId, connection)).doesNotThrowAnyException();

        assertThat(connection.joinedRoomsView()).containsExactly(roomId);
        assertThat(index.hasConnections(roomId)).isTrue();
        assertThat(connectionIds(index, roomId)).containsExactly("c1");
        assertThat(directory.operations()).containsExactly("activate:" + roomId);
    }

    @Test
    void leaveKeepsLocalMembershipRemovedWhenDeactivationFails() {
        RecordingDirectory directory = new RecordingDirectory();
        RoomLocalIndex index = new RoomLocalIndex();
        RoomLocalPresenceService service = service(index, directory);
        WsConnection connection = connection("c1");
        UUID roomId = uuid(2);
        service.joinLocalRoom(roomId, connection);
        directory.failNextDeactivation(roomId);

        assertThatCode(() -> service.leaveLocalRoom(roomId, connection)).doesNotThrowAnyException();

        assertThat(connection.joinedRoomsView()).doesNotContain(roomId);
        assertThat(index.hasConnections(roomId)).isFalse();
        assertThat(connectionIds(index, roomId)).isEmpty();
        assertThat(directory.operations()).containsExactly(
                "activate:" + roomId,
                "deactivate:" + roomId
        );
    }

    @Test
    void heartbeatRetriesPendingActivationAndDeactivation() {
        RecordingDirectory directory = new RecordingDirectory();
        RoomLocalPresenceService service = service(new RoomLocalIndex(), directory);
        WsConnection connection = connection("c1");
        UUID roomId = uuid(3);
        directory.failNextActivation(roomId);

        service.joinLocalRoom(roomId, connection);
        service.refreshPresence();

        assertThat(directory.activeWorkerIds(roomId)).containsExactly("worker-a");
        directory.failNextDeactivation(roomId);
        service.leaveLocalRoom(roomId, connection);
        service.refreshPresence();

        assertThat(directory.activeWorkerIds(roomId)).isEmpty();
        assertThat(directory.operations()).containsExactly(
                "activate:" + roomId,
                "activate:" + roomId,
                "deactivate:" + roomId,
                "deactivate:" + roomId
        );
    }

    @Test
    void oneRoomRefreshFailureDoesNotBlockAnotherRoom() {
        RecordingDirectory directory = new RecordingDirectory();
        RoomLocalPresenceService service = service(new RoomLocalIndex(), directory);
        UUID firstRoomId = uuid(4);
        UUID secondRoomId = uuid(5);
        service.joinLocalRoom(firstRoomId, connection("c1"));
        service.joinLocalRoom(secondRoomId, connection("c2"));
        directory.clearOperations();
        directory.failNextActivation(firstRoomId);

        service.refreshPresence();

        assertThat(directory.operations()).containsExactlyInAnyOrder(
                "activate:" + firstRoomId,
                "activate:" + secondRoomId
        );
    }

    @Test
    void heartbeatRechecksOccupancyBeforeRetryingFailedActivation() {
        RecordingDirectory directory = new RecordingDirectory();
        RoomLocalIndex index = new RoomLocalIndex();
        RoomLocalPresenceService service = service(index, directory);
        WsConnection connection = connection("c1");
        UUID roomId = uuid(6);
        directory.failNextActivation(roomId);
        directory.failNextDeactivation(roomId);

        service.joinLocalRoom(roomId, connection);
        service.leaveLocalRoom(roomId, connection);
        service.refreshPresence();

        assertThat(index.hasConnections(roomId)).isFalse();
        assertThat(directory.activeWorkerIds(roomId)).isEmpty();
        assertThat(directory.operations()).containsExactly(
                "activate:" + roomId,
                "deactivate:" + roomId,
                "deactivate:" + roomId
        );
    }

    @Test
    void concurrentFinalLeaveAndNewJoinRemainConsistent() throws Exception {
        BlockingDirectory directory = new BlockingDirectory();
        RoomLocalIndex index = new RoomLocalIndex();
        RoomLocalPresenceService service = service(index, directory);
        UUID roomId = uuid(7);
        WsConnection leaving = connection("leaving");
        WsConnection joining = connection("joining");
        service.joinLocalRoom(roomId, leaving);
        directory.blockNextDeactivation();

        Thread leaveThread = new Thread(() -> service.leaveLocalRoom(roomId, leaving));
        Thread joinThread = new Thread(() -> service.joinLocalRoom(roomId, joining));
        leaveThread.start();
        assertThat(directory.awaitBlockedDeactivation()).isTrue();
        joinThread.start();
        await().atMost(Duration.ofSeconds(1)).until(joinThread::isAlive);
        directory.releaseDeactivation();
        leaveThread.join(TimeUnit.SECONDS.toMillis(2));
        joinThread.join(TimeUnit.SECONDS.toMillis(2));

        assertThat(leaveThread.isAlive()).isFalse();
        assertThat(joinThread.isAlive()).isFalse();
        assertThat(leaving.joinedRoomsView()).doesNotContain(roomId);
        assertThat(joining.joinedRoomsView()).containsExactly(roomId);
        assertThat(index.hasConnections(roomId)).isTrue();
        assertThat(connectionIds(index, roomId)).containsExactly("joining");
        assertThat(directory.operations()).endsWith(
                "deactivate:" + roomId,
                "activate:" + roomId
        );
        assertThat(directory.activeWorkerIds(roomId)).containsExactly("worker-a");
    }

    private static RoomLocalPresenceService service(RoomLocalIndex index, RoomPresenceDirectory directory) {
        return new RoomLocalPresenceService(index, directory, "worker-a");
    }

    private static WsConnection connection(String connectionId) {
        return new WsConnection(connectionId, mock(WebSocketSession.class), 10);
    }

    private static List<String> connectionIds(RoomLocalIndex index, UUID roomId) {
        List<String> ids = new ArrayList<>();
        index.forEachConnectionId(roomId, ids::add);
        return ids;
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static class RecordingDirectory implements RoomPresenceDirectory {

        private final List<String> operations = java.util.Collections.synchronizedList(new ArrayList<>());
        private final Set<String> activeWorkerKeys = ConcurrentHashMap.newKeySet();
        private final Set<UUID> failActivationRoomIds = ConcurrentHashMap.newKeySet();
        private final Set<UUID> failDeactivationRoomIds = ConcurrentHashMap.newKeySet();

        @Override
        public void activate(UUID roomId, String workerId) {
            operations.add("activate:" + roomId);
            if (failActivationRoomIds.remove(roomId)) {
                throw new IllegalStateException("activation failed");
            }
            activeWorkerKeys.add(key(workerId, roomId));
        }

        @Override
        public void deactivate(UUID roomId, String workerId) {
            operations.add("deactivate:" + roomId);
            if (failDeactivationRoomIds.remove(roomId)) {
                throw new IllegalStateException("deactivation failed");
            }
            activeWorkerKeys.remove(key(workerId, roomId));
        }

        @Override
        public Set<String> activeWorkerIds(UUID roomId) {
            Set<String> workerIds = ConcurrentHashMap.newKeySet();
            for (String key : activeWorkerKeys) {
                if (key.endsWith(":" + roomId)) {
                    workerIds.add(key.substring(0, key.indexOf(':')));
                }
            }
            return Set.copyOf(workerIds);
        }

        void failNextActivation(UUID roomId) {
            failActivationRoomIds.add(roomId);
        }

        void failNextDeactivation(UUID roomId) {
            failDeactivationRoomIds.add(roomId);
        }

        List<String> operations() {
            synchronized (operations) {
                return List.copyOf(operations);
            }
        }

        void clearOperations() {
            operations.clear();
        }

        private static String key(String workerId, UUID roomId) {
            return workerId + ":" + roomId;
        }
    }

    private static final class BlockingDirectory extends RecordingDirectory {

        private final AtomicBoolean blockNextDeactivation = new AtomicBoolean(false);
        private final CountDownLatch deactivationBlocked = new CountDownLatch(1);
        private final CountDownLatch releaseDeactivation = new CountDownLatch(1);

        @Override
        public void deactivate(UUID roomId, String workerId) {
            if (blockNextDeactivation.compareAndSet(true, false)) {
                deactivationBlocked.countDown();
                try {
                    if (!releaseDeactivation.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting to release deactivation");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while blocking deactivation", ex);
                }
            }
            super.deactivate(roomId, workerId);
        }

        void blockNextDeactivation() {
            blockNextDeactivation.set(true);
        }

        boolean awaitBlockedDeactivation() throws InterruptedException {
            return deactivationBlocked.await(2, TimeUnit.SECONDS);
        }

        void releaseDeactivation() {
            releaseDeactivation.countDown();
        }
    }
}
