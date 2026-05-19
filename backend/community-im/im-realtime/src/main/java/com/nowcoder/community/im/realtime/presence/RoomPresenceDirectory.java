package com.nowcoder.community.im.realtime.presence;

import java.util.Set;
import java.util.UUID;

public interface RoomPresenceDirectory {

    void activate(UUID roomId, String workerId);

    void deactivate(UUID roomId, String workerId);

    Set<String> activeWorkerIds(UUID roomId);
}
