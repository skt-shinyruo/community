package com.nowcoder.community.im.realtime.presence;

import java.util.Set;
import java.util.UUID;

public class NoopRoomPresenceDirectory implements RoomPresenceDirectory {

    @Override
    public void activate(UUID roomId, String workerId) {
    }

    @Override
    public void deactivate(UUID roomId, String workerId) {
    }

    @Override
    public Set<String> activeWorkerIds(UUID roomId) {
        return Set.of();
    }
}
