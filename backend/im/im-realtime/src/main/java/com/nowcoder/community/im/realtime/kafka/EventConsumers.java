package com.nowcoder.community.im.realtime.kafka;

import com.nowcoder.community.im.contracts.ImTopics;
import com.nowcoder.community.im.contracts.event.PrivateMessagePersistedEventV1;
import com.nowcoder.community.im.contracts.event.RoomMemberChangedEventV1;
import com.nowcoder.community.im.contracts.event.RoomMessagePersistedEventV1;
import com.nowcoder.community.im.realtime.presence.ConnectionRegistry;
import com.nowcoder.community.im.realtime.presence.RoomLocalIndex;
import com.nowcoder.community.im.realtime.push.PrivatePushService;
import com.nowcoder.community.im.realtime.push.RoomFanoutCoalescer;
import com.nowcoder.community.im.realtime.push.RoomUpdateCoalescer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class EventConsumers {

    private final PrivatePushService privatePushService;
    private final ConnectionRegistry connectionRegistry;
    private final RoomLocalIndex roomLocalIndex;
    private final RoomUpdateCoalescer roomUpdateCoalescer;
    private final RoomFanoutCoalescer roomFanoutCoalescer;

    public EventConsumers(
            PrivatePushService privatePushService,
            ConnectionRegistry connectionRegistry,
            RoomLocalIndex roomLocalIndex,
            RoomUpdateCoalescer roomUpdateCoalescer,
            RoomFanoutCoalescer roomFanoutCoalescer
    ) {
        this.privatePushService = privatePushService;
        this.connectionRegistry = connectionRegistry;
        this.roomLocalIndex = roomLocalIndex;
        this.roomUpdateCoalescer = roomUpdateCoalescer;
        this.roomFanoutCoalescer = roomFanoutCoalescer;
    }

    @KafkaListener(
            topics = ImTopics.EVENT_PRIVATE_PERSISTED_V1,
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${im.kafka.event.concurrency:3}"
    )
    public void onPrivatePersisted(PrivateMessagePersistedEventV1 event) {
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
        roomFanoutCoalescer.markRoomUpdated(event.roomId(), event.seq());
    }

    @KafkaListener(topics = ImTopics.EVENT_ROOM_MEMBER_CHANGED_V1, containerFactory = "kafkaListenerContainerFactory")
    public void onRoomMemberChanged(RoomMemberChangedEventV1 event) {
        if (event == null) {
            return;
        }
        long roomId = event.roomId();
        int userId = event.userId();
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
