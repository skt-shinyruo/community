package com.nowcoder.community.im.core.kafka;

import com.nowcoder.community.im.common.ImTopics;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEvent;
import com.nowcoder.community.im.common.event.PrivateMessageRejectedEvent;
import com.nowcoder.community.im.common.event.RoomMemberChanged;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import com.nowcoder.community.im.common.event.RoomMessageRejectedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public EventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishPrivatePersisted(PrivateMessagePersistedEvent event) {
        if (event == null) {
            return;
        }
        kafkaTemplate.send(ImTopics.EVENT_PRIVATE_PERSISTED, event.conversationId(), event);
    }

    public void publishPrivateRejected(PrivateMessageRejectedEvent event) {
        if (event == null) {
            return;
        }
        kafkaTemplate.send(ImTopics.EVENT_PRIVATE_REJECTED, event.conversationId(), event);
    }

    public void publishRoomPersisted(RoomMessagePersistedEvent event) {
        if (event == null) {
            return;
        }
        kafkaTemplate.send(ImTopics.EVENT_ROOM_PERSISTED, String.valueOf(event.roomId()), event);
    }

    public void publishRoomRejected(RoomMessageRejectedEvent event) {
        if (event == null) {
            return;
        }
        kafkaTemplate.send(ImTopics.EVENT_ROOM_REJECTED, String.valueOf(event.roomId()), event);
    }

    public void publishRoomMemberChanged(RoomMemberChanged event) {
        if (event == null) {
            return;
        }
        kafkaTemplate.send(ImTopics.EVENT_ROOM_MEMBER_CHANGED, String.valueOf(event.roomId()), event);
    }
}
