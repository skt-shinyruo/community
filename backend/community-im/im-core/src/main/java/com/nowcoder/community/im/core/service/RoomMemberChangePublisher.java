package com.nowcoder.community.im.core.service;

import java.util.UUID;

public interface RoomMemberChangePublisher {

    void publishJoined(UUID roomId, UUID userId);

    void publishLeft(UUID roomId, UUID userId);
}
