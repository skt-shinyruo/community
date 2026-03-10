package com.nowcoder.community.im.core.kafka;

import com.nowcoder.community.im.contracts.ImTopics;
import com.nowcoder.community.im.contracts.event.PrivateMessagePersistedEventV1;
import com.nowcoder.community.im.contracts.event.RoomMemberChangedEventV1;
import com.nowcoder.community.im.contracts.event.RoomMessagePersistedEventV1;
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

    public void publishRoomPersisted(RoomMessagePersistedEventV1 event) {
        if (event == null) {
            return;
        }
        kafkaTemplate.send(ImTopics.EVENT_ROOM_PERSISTED_V1, String.valueOf(event.roomId()), event);
    }

    public void publishRoomMemberChanged(RoomMemberChangedEventV1 event) {
        if (event == null) {
            return;
        }
        kafkaTemplate.send(ImTopics.EVENT_ROOM_MEMBER_CHANGED_V1, String.valueOf(event.roomId()), event);
    }
}

