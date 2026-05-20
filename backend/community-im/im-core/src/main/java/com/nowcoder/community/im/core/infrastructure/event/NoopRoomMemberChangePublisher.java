package com.nowcoder.community.im.core.infrastructure.event;

import com.nowcoder.community.im.core.domain.event.RoomMemberChangePublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "im.room-member-change", name = "publisher", havingValue = "noop", matchIfMissing = true)
public class NoopRoomMemberChangePublisher implements RoomMemberChangePublisher {

    @Override
    public void publishJoined(UUID roomId, UUID userId, long version) {
    }

    @Override
    public void publishLeft(UUID roomId, UUID userId, long version) {
    }
}
