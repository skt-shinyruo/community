package com.nowcoder.community.im.realtime.presence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomLocalPresenceService {

    private static final Logger log = LoggerFactory.getLogger(RoomLocalPresenceService.class);
    private static final int ROOM_LOCK_STRIPES = 256;

    private final RoomLocalIndex roomLocalIndex;
    private final RoomPresenceDirectory roomPresenceDirectory;
    private final String workerId;
    private final Set<UUID> activeRoomIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingRoomIds = ConcurrentHashMap.newKeySet();
    private final Object[] roomLocks = new Object[ROOM_LOCK_STRIPES];

    public RoomLocalPresenceService(
            RoomLocalIndex roomLocalIndex,
            RoomPresenceDirectory roomPresenceDirectory,
            @Value("${im.session.worker-id:${HOSTNAME:local}}") String workerId
    ) {
        this.roomLocalIndex = roomLocalIndex;
        this.roomPresenceDirectory = roomPresenceDirectory;
        this.workerId = StringUtils.hasText(workerId) ? workerId.trim() : "local";
        for (int index = 0; index < roomLocks.length; index++) {
            roomLocks[index] = new Object();
        }
    }

    public void joinLocalRoom(UUID roomId, WsConnection connection) {
        reconcileLocalMembership(roomId, connection, true);
    }

    public void leaveLocalRoom(UUID roomId, WsConnection connection) {
        reconcileLocalMembership(roomId, connection, false);
    }

    public void reconcileLocalMembership(UUID roomId, WsConnection connection, boolean expectedMember) {
        if (roomId == null || connection == null) {
            return;
        }
        synchronized (lockFor(roomId)) {
            if (expectedMember) {
                connection.joinRoom(roomId);
                roomLocalIndex.add(roomId, connection.connectionId());
            } else {
                connection.leaveRoom(roomId);
                roomLocalIndex.remove(roomId, connection.connectionId());
            }
            reconcilePresenceLocked(roomId);
        }
    }

    public void refreshPresence() {
        Set<UUID> roomIds = new HashSet<>(activeRoomIds);
        roomIds.addAll(pendingRoomIds);
        for (UUID roomId : roomIds) {
            synchronized (lockFor(roomId)) {
                reconcilePresenceLocked(roomId);
            }
        }
    }

    private void reconcilePresenceLocked(UUID roomId) {
        boolean shouldBeActive = roomLocalIndex.hasConnections(roomId);
        if (shouldBeActive) {
            activeRoomIds.add(roomId);
        } else {
            activeRoomIds.remove(roomId);
        }
        try {
            if (shouldBeActive) {
                roomPresenceDirectory.activate(roomId, workerId);
            } else {
                roomPresenceDirectory.deactivate(roomId, workerId);
            }
            pendingRoomIds.remove(roomId);
        } catch (RuntimeException failure) {
            pendingRoomIds.add(roomId);
            log.warn(
                    "[room-presence] reconciliation failed: roomId={}, workerId={}, active={}, error={}",
                    roomId,
                    workerId,
                    shouldBeActive,
                    failure.toString()
            );
        }
    }

    private Object lockFor(UUID roomId) {
        return roomLocks[Math.floorMod(roomId.hashCode(), roomLocks.length)];
    }
}
