package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.realtime.presence.RoomPresenceDirectory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RoomFanoutRoutingService {

    private final RoomPresenceDirectory roomPresenceDirectory;
    private final RoomFanoutPlanner roomFanoutPlanner;

    public RoomFanoutRoutingService(
            RoomPresenceDirectory roomPresenceDirectory,
            RoomFanoutPlanner roomFanoutPlanner
    ) {
        this.roomPresenceDirectory = roomPresenceDirectory;
        this.roomFanoutPlanner = roomFanoutPlanner;
    }

    public List<RoomFanoutRoute> routesFor(UUID roomId, long lastSeq) {
        if (roomId == null || lastSeq <= 0) {
            return List.of();
        }
        return roomFanoutPlanner.plan(roomId, lastSeq, roomPresenceDirectory.activeWorkerIds(roomId));
    }
}
