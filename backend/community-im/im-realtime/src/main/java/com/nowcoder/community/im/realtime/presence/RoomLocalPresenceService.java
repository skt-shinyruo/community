package com.nowcoder.community.im.realtime.presence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomLocalPresenceService {

    private final RoomLocalIndex roomLocalIndex;
    private final RoomPresenceDirectory roomPresenceDirectory;
    private final String workerId;
    private final Set<UUID> activeRoomIds = ConcurrentHashMap.newKeySet();

    public RoomLocalPresenceService(
            RoomLocalIndex roomLocalIndex,
            RoomPresenceDirectory roomPresenceDirectory,
            @Value("${im.session.worker-id:${HOSTNAME:local}}") String workerId
    ) {
        this.roomLocalIndex = roomLocalIndex;
        this.roomPresenceDirectory = roomPresenceDirectory;
        this.workerId = StringUtils.hasText(workerId) ? workerId.trim() : "local";
    }

    public void addLocalConnection(UUID roomId, String connectionId) {
        if (roomLocalIndex.add(roomId, connectionId)) {
            activeRoomIds.add(roomId);
            roomPresenceDirectory.activate(roomId, workerId);
        }
    }

    public void removeLocalConnection(UUID roomId, String connectionId) {
        if (roomLocalIndex.remove(roomId, connectionId)) {
            activeRoomIds.remove(roomId);
            roomPresenceDirectory.deactivate(roomId, workerId);
        }
    }

    public void refreshActiveRooms() {
        for (UUID roomId : activeRoomIds) {
            roomPresenceDirectory.activate(roomId, workerId);
        }
    }
}
