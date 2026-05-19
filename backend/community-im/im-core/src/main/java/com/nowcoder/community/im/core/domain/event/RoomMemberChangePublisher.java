package com.nowcoder.community.im.core.domain.event;

import java.util.UUID;

public interface RoomMemberChangePublisher {

    void publishJoined(UUID roomId, UUID userId);

    void publishLeft(UUID roomId, UUID userId);
}
