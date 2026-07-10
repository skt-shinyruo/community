package com.nowcoder.community.im.core.kafka;

import com.nowcoder.community.im.common.event.RoomMemberChanged;
import com.nowcoder.community.im.common.projection.ProjectionVersions;
import com.nowcoder.community.im.core.domain.event.RoomMemberChangePublisher;
import com.nowcoder.community.im.core.outbox.ImMessageOutboxEnqueuer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "im.room-member-change", name = "publisher", havingValue = "kafka")
public class KafkaRoomMemberChangePublisher implements RoomMemberChangePublisher {

    private final ImMessageOutboxEnqueuer outboxEnqueuer;

    public KafkaRoomMemberChangePublisher(ImMessageOutboxEnqueuer outboxEnqueuer) {
        this.outboxEnqueuer = outboxEnqueuer;
    }

    @Override
    public void publishJoined(UUID roomId, UUID userId, long version) {
        ProjectionVersions.requirePositive(version, "version");
        Instant now = Instant.now();
        outboxEnqueuer.enqueueRoomMemberChanged(new RoomMemberChanged(
                newEventId(),
                roomId,
                userId,
                "JOINED",
                now.toEpochMilli(),
                version
        ));
    }

    @Override
    public void publishLeft(UUID roomId, UUID userId, long version) {
        ProjectionVersions.requirePositive(version, "version");
        Instant now = Instant.now();
        outboxEnqueuer.enqueueRoomMemberChanged(new RoomMemberChanged(
                newEventId(),
                roomId,
                userId,
                "LEFT",
                now.toEpochMilli(),
                version
        ));
    }

    private String newEventId() {
        return "evt_room_member_" + UUID.randomUUID();
    }
}
