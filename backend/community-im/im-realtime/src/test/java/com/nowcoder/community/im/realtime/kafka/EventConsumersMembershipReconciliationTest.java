package com.nowcoder.community.im.realtime.kafka;

import com.nowcoder.community.im.common.event.RoomMemberChanged;
import com.nowcoder.community.im.realtime.presence.ConnectionRegistry;
import com.nowcoder.community.im.realtime.presence.RoomLocalIndex;
import com.nowcoder.community.im.realtime.presence.RoomLocalPresenceService;
import com.nowcoder.community.im.realtime.presence.RoomPresenceDirectory;
import com.nowcoder.community.im.realtime.projection.MembershipProjectionService;
import com.nowcoder.community.im.realtime.projection.PolicyProjectionService;
import com.nowcoder.community.im.realtime.push.PrivatePushService;
import com.nowcoder.community.im.realtime.push.SendResultPushService;
import com.nowcoder.community.im.realtime.service.ConnectionLifecycleService;
import com.nowcoder.community.im.realtime.session.ConnectionSession;
import com.nowcoder.community.im.realtime.session.ConnectionState;
import com.nowcoder.community.im.realtime.session.InMemoryConnectionOutput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventConsumersMembershipReconciliationTest {

    @Test
    void duplicateJoinReconcilesEveryConnectionFromCurrentProjection() {
        UUID roomId = uuid(1);
        UUID userId = user(1);
        RoomMemberChanged event = event(roomId, userId, "JOINED", 10L);
        MembershipProjectionService projection = mock(MembershipProjectionService.class);
        RoomLocalPresenceService presence = mock(RoomLocalPresenceService.class);
        ConnectionRegistry registry = registryWith(userId, connection("c1", userId), connection("c2", userId));
        when(projection.applyRoomMemberChanged(event)).thenReturn(false);
        when(projection.isMember(roomId, userId)).thenReturn(true);

        consumer(projection, registry, presence).onRoomMemberChanged(event);

        for (ConnectionState connection : registry.listByUserId(userId)) {
            verify(presence).reconcileLocalMembership(roomId, connection, true);
        }
    }

    @Test
    void staleLeaveReconcilesEveryConnectionFromCurrentProjection() {
        UUID roomId = uuid(2);
        UUID userId = user(2);
        RoomMemberChanged event = event(roomId, userId, "LEFT", 9L);
        MembershipProjectionService projection = mock(MembershipProjectionService.class);
        RoomLocalPresenceService presence = mock(RoomLocalPresenceService.class);
        ConnectionRegistry registry = registryWith(userId, connection("c1", userId), connection("c2", userId));
        when(projection.applyRoomMemberChanged(event)).thenReturn(false);
        when(projection.isMember(roomId, userId)).thenReturn(false);

        consumer(projection, registry, presence).onRoomMemberChanged(event);

        for (ConnectionState connection : registry.listByUserId(userId)) {
            verify(presence).reconcileLocalMembership(roomId, connection, false);
        }
    }

    @Test
    void duplicateDeliveryRepairsFailedPresenceWithoutRegressingLocalMembership() {
        UUID roomId = uuid(3);
        UUID userId = user(3);
        RoomMemberChanged event = event(roomId, userId, "JOINED", 10L);
        MembershipProjectionService projection = mock(MembershipProjectionService.class);
        ConnectionRegistry registry = new ConnectionRegistry();
        ConnectionSession connection = connection("c1", userId);
        registry.register(connection);
        FailOnceActivationDirectory directory = new FailOnceActivationDirectory();
        RoomLocalIndex index = new RoomLocalIndex();
        RoomLocalPresenceService presence = new RoomLocalPresenceService(index, directory, "worker-a");
        when(projection.applyRoomMemberChanged(event)).thenReturn(false);
        when(projection.isMember(roomId, userId)).thenReturn(true);
        EventConsumers consumer = consumer(projection, registry, presence);

        consumer.onRoomMemberChanged(event);
        assertThat(connection.joinedRoomsView()).containsExactly(roomId);
        assertThat(index.hasConnections(roomId)).isTrue();

        consumer.onRoomMemberChanged(event);

        assertThat(connection.joinedRoomsView()).containsExactly(roomId);
        assertThat(index.hasConnections(roomId)).isTrue();
        assertThat(directory.activations).containsExactly(roomId, roomId);
        assertThat(directory.activeWorkerIds(roomId)).containsExactly("worker-a");
    }

    private static EventConsumers consumer(
            MembershipProjectionService projection,
            ConnectionRegistry registry,
            RoomLocalPresenceService presence
    ) {
        return new EventConsumers(
                mock(PrivatePushService.class),
                projection,
                mock(PolicyProjectionService.class),
                new ConnectionLifecycleService(registry, presence, projection),
                mock(SendResultPushService.class)
        );
    }

    private static ConnectionRegistry registryWith(UUID userId, ConnectionSession... connections) {
        ConnectionRegistry registry = new ConnectionRegistry();
        for (ConnectionSession connection : connections) {
            assertThat(connection.userId()).isEqualTo(userId);
            registry.register(connection);
        }
        return registry;
    }

    private static ConnectionSession connection(String connectionId, UUID userId) {
        ConnectionSession connection = new ConnectionSession(connectionId, new InMemoryConnectionOutput());
        connection.bindUser(userId);
        return connection;
    }

    private static RoomMemberChanged event(UUID roomId, UUID userId, String action, long version) {
        return new RoomMemberChanged("event-" + version, roomId, userId, action, 1_000L, version);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static UUID user(long suffix) {
        return UUID.fromString("00000000-0000-7001-8000-" + String.format("%012x", suffix));
    }

    private static final class FailOnceActivationDirectory implements RoomPresenceDirectory {

        private final List<UUID> activations = new ArrayList<>();
        private boolean fail = true;
        private String activeWorkerId;

        @Override
        public void activate(UUID roomId, String workerId) {
            activations.add(roomId);
            if (fail) {
                fail = false;
                throw new IllegalStateException("redis unavailable");
            }
            activeWorkerId = workerId;
        }

        @Override
        public void deactivate(UUID roomId, String workerId) {
            activeWorkerId = null;
        }

        @Override
        public Set<String> activeWorkerIds(UUID roomId) {
            return activeWorkerId == null ? Set.of() : Set.of(activeWorkerId);
        }
    }
}
