package com.nowcoder.community.im.core.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "im.room-member-change", name = "publisher", havingValue = "noop", matchIfMissing = true)
public class NoopRoomMemberChangePublisher implements RoomMemberChangePublisher {

    @Override
    public void publishJoined(UUID roomId, UUID userId) {
    }

    @Override
    public void publishLeft(UUID roomId, UUID userId) {
    }
}
