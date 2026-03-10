package com.nowcoder.community.im.core.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "im.room-member-change", name = "publisher", havingValue = "noop", matchIfMissing = true)
public class NoopRoomMemberChangePublisher implements RoomMemberChangePublisher {

    @Override
    public void publishJoined(long roomId, int userId) {
    }

    @Override
    public void publishLeft(long roomId, int userId) {
    }
}
