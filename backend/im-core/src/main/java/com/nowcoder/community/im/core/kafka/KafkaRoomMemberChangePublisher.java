package com.nowcoder.community.im.core.kafka;

import com.nowcoder.community.im.contracts.event.RoomMemberChangedEventV1;
import com.nowcoder.community.im.core.service.RoomMemberChangePublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(prefix = "im.room-member-change", name = "publisher", havingValue = "kafka")
public class KafkaRoomMemberChangePublisher implements RoomMemberChangePublisher {

    private final EventProducer eventProducer;

    public KafkaRoomMemberChangePublisher(EventProducer eventProducer) {
        this.eventProducer = eventProducer;
    }

    @Override
    public void publishJoined(long roomId, int userId) {
        Instant now = Instant.now();
        eventProducer.publishRoomMemberChanged(new RoomMemberChangedEventV1(
                "evt_room_member_joined_" + roomId + "_" + userId + "_" + now.toEpochMilli(),
                roomId,
                userId,
                "JOINED",
                now.toEpochMilli()
        ));
    }

    @Override
    public void publishLeft(long roomId, int userId) {
        Instant now = Instant.now();
        eventProducer.publishRoomMemberChanged(new RoomMemberChangedEventV1(
                "evt_room_member_left_" + roomId + "_" + userId + "_" + now.toEpochMilli(),
                roomId,
                userId,
                "LEFT",
                now.toEpochMilli()
        ));
    }
}
