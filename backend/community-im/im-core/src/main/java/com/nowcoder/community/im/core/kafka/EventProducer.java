package com.nowcoder.community.im.core.kafka;

import com.nowcoder.community.im.common.ImTopics;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEventV1;
import com.nowcoder.community.im.common.event.PrivateMessageRejectedEventV1;
import com.nowcoder.community.im.common.event.RoomMemberChanged;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEventV1;
import com.nowcoder.community.im.common.event.RoomMessageRejectedEventV1;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public EventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishPrivatePersisted(PrivateMessagePersistedEventV1 event) {
        if (event == null) {
            return;
        }
        kafkaTemplate.send(ImTopics.EVENT_PRIVATE_PERSISTED_V1, event.conversationId(), event);
    }

    public void publishPrivateRejected(PrivateMessageRejectedEventV1 event) {
        if (event == null) {
            return;
        }
        kafkaTemplate.send(ImTopics.EVENT_PRIVATE_REJECTED_V1, event.conversationId(), event);
    }

    public void publishRoomPersisted(RoomMessagePersistedEventV1 event) {
        if (event == null) {
            return;
        }
        kafkaTemplate.send(ImTopics.EVENT_ROOM_PERSISTED_V1, String.valueOf(event.roomId()), event);
    }

    public void publishRoomRejected(RoomMessageRejectedEventV1 event) {
        if (event == null) {
            return;
        }
        kafkaTemplate.send(ImTopics.EVENT_ROOM_REJECTED_V1, String.valueOf(event.roomId()), event);
    }

    public void publishRoomMemberChanged(RoomMemberChanged event) {
        if (event == null) {
            return;
        }
        kafkaTemplate.send(ImTopics.EVENT_ROOM_MEMBER_CHANGED, String.valueOf(event.roomId()), event);
    }
}
