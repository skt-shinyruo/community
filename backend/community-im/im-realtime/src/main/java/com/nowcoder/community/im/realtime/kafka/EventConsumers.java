package com.nowcoder.community.im.realtime.kafka;

import com.nowcoder.community.im.common.ImTopics;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEventV1;
import com.nowcoder.community.im.common.event.PrivateMessageRejectedEventV1;
import com.nowcoder.community.im.common.event.RoomMemberChangedEventV1;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEventV1;
import com.nowcoder.community.im.common.event.RoomMessageRejectedEventV1;
import com.nowcoder.community.im.realtime.presence.ConnectionRegistry;
import com.nowcoder.community.im.realtime.presence.RoomLocalIndex;
import com.nowcoder.community.im.realtime.push.PrivatePushService;
import com.nowcoder.community.im.realtime.push.RoomFanoutCoalescer;
import com.nowcoder.community.im.realtime.push.RoomUpdateCoalescer;
import com.nowcoder.community.im.realtime.push.SendResultPushService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EventConsumers {

    private final PrivatePushService privatePushService;
    private final ConnectionRegistry connectionRegistry;
    private final RoomLocalIndex roomLocalIndex;
    private final RoomUpdateCoalescer roomUpdateCoalescer;
    private final RoomFanoutCoalescer roomFanoutCoalescer;
    private final SendResultPushService sendResultPushService;

    public EventConsumers(
            PrivatePushService privatePushService,
            ConnectionRegistry connectionRegistry,
            RoomLocalIndex roomLocalIndex,
            RoomUpdateCoalescer roomUpdateCoalescer,
            RoomFanoutCoalescer roomFanoutCoalescer,
            SendResultPushService sendResultPushService
    ) {
        this.privatePushService = privatePushService;
        this.connectionRegistry = connectionRegistry;
        this.roomLocalIndex = roomLocalIndex;
        this.roomUpdateCoalescer = roomUpdateCoalescer;
        this.roomFanoutCoalescer = roomFanoutCoalescer;
        this.sendResultPushService = sendResultPushService;
    }

    @KafkaListener(
            topics = ImTopics.EVENT_PRIVATE_PERSISTED_V1,
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${im.kafka.event.concurrency:3}"
    )
    public void onPrivatePersisted(PrivateMessagePersistedEventV1 event) {
        sendResultPushService.pushPrivateCommitted(event);
        privatePushService.pushPrivateMessage(event);
    }

    @KafkaListener(
            topics = ImTopics.EVENT_ROOM_PERSISTED_V1,
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${im.kafka.event.concurrency:3}"
    )
    public void onRoomPersisted(RoomMessagePersistedEventV1 event) {
        if (event == null) {
            return;
        }
        sendResultPushService.pushRoomCommitted(event);
        roomFanoutCoalescer.markRoomUpdated(event.roomId(), event.seq());
    }

    @KafkaListener(
            topics = ImTopics.EVENT_PRIVATE_REJECTED_V1,
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${im.kafka.event.concurrency:3}"
    )
    public void onPrivateRejected(PrivateMessageRejectedEventV1 event) {
        sendResultPushService.pushPrivateRejected(event);
    }

    @KafkaListener(
            topics = ImTopics.EVENT_ROOM_REJECTED_V1,
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${im.kafka.event.concurrency:3}"
    )
    public void onRoomRejected(RoomMessageRejectedEventV1 event) {
        sendResultPushService.pushRoomRejected(event);
    }

    @KafkaListener(topics = ImTopics.EVENT_ROOM_MEMBER_CHANGED_V1, containerFactory = "kafkaListenerContainerFactory")
    public void onRoomMemberChanged(RoomMemberChangedEventV1 event) {
        if (event == null) {
            return;
        }
        UUID roomId = event.roomId();
        UUID userId = event.userId();
        String action = event.action() == null ? "" : event.action().trim().toUpperCase();
        if ("JOINED".equals(action)) {
            connectionRegistry.forEachConnectionByUserId(userId, conn -> {
                roomLocalIndex.add(roomId, conn.connectionId());
                conn.joinRoom(roomId);
            });
            return;
        }
        if ("LEFT".equals(action)) {
            connectionRegistry.forEachConnectionByUserId(userId, conn -> {
                roomLocalIndex.remove(roomId, conn.connectionId());
                conn.leaveRoom(roomId);
            });
        }
    }
}
