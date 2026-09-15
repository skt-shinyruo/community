package com.nowcoder.community.im.realtime.service;

import com.nowcoder.community.im.realtime.presence.ConnectionRegistry;
import com.nowcoder.community.im.realtime.presence.RoomLocalPresenceService;
import com.nowcoder.community.im.realtime.projection.MembershipProjectionService;
import com.nowcoder.community.im.realtime.session.ConnectionSession;
import com.nowcoder.community.im.realtime.session.RealtimeEventLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * Connection lifecycle orchestration: binds an authenticated connection to the
 * registry and its already-joined rooms, reconciles local room presence when the
 * membership projection changes, and releases everything on disconnect. The
 * membership projection only supplies membership state; all room binding/presence
 * orchestration lives here.
 */
@Service
public class ConnectionLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionLifecycleService.class);

    private final ConnectionRegistry connectionRegistry;
    private final RoomLocalPresenceService roomLocalPresenceService;
    private final MembershipProjectionService membershipProjectionService;

    public ConnectionLifecycleService(
            ConnectionRegistry connectionRegistry,
            RoomLocalPresenceService roomLocalPresenceService,
            MembershipProjectionService membershipProjectionService
    ) {
        this.connectionRegistry = connectionRegistry;
        this.roomLocalPresenceService = roomLocalPresenceService;
        this.membershipProjectionService = membershipProjectionService;
    }

    /**
     * Binds ticket identity to the connection, joins every room the membership
     * projection already knows for this user, then registers the connection. Rooms
     * are joined before registration so a registered connection always reflects its
     * full local presence.
     */
    public void connect(ConnectionSession connection, String sessionId, UUID userId, String workerId) {
        connection.bindSession(sessionId, userId, workerId);
        for (UUID roomId : membershipProjectionService.roomIdsForUser(userId)) {
            roomLocalPresenceService.joinLocalRoom(roomId, connection);
        }
        connectionRegistry.register(connection);
    }

    /**
     * Reconciles every local connection of {@code userId} with the membership state
     * the projection currently holds for {@code roomId}.
     */
    public void reconcileRoomMembership(UUID roomId, UUID userId, boolean expectedMember) {
        connectionRegistry.forEachConnectionByUserId(
                userId,
                connection -> roomLocalPresenceService.reconcileLocalMembership(roomId, connection, expectedMember)
        );
    }

    /**
     * Unregisters the connection, leaves every joined room and completes the outbound
     * stream. Steps are individually failure-tolerant so one broken room never skips
     * the remaining cleanup; the access log is always emitted.
     */
    public void disconnect(ConnectionSession connection) {
        if (connection == null) {
            return;
        }
        int joinedRoomCount = connection.joinedRoomsView().size();
        int outboundBacklog = connection.output().outboundBacklog();
        try {
            connectionRegistry.unregister(connection);
        } catch (RuntimeException ignore) {
        }
        for (UUID roomId : Set.copyOf(connection.joinedRoomsView())) {
            try {
                roomLocalPresenceService.leaveLocalRoom(roomId, connection);
            } catch (RuntimeException ignore) {
            }
        }
        try {
            connection.output().complete();
        } catch (RuntimeException ignore) {
        } finally {
            RealtimeEventLog.info(
                    log,
                    RealtimeEventLog.CATEGORY_ACCESS,
                    "ws_disconnect",
                    "success",
                    connection.traceId(),
                    "community.connection_id", connection.connectionId(),
                    "user.id", connection.userId(),
                    "community.joined_room_count", joinedRoomCount,
                    "community.outbound_backlog", outboundBacklog
            );
        }
    }
}
