package com.nowcoder.community.im.realtime.kafka;

import com.nowcoder.community.im.common.event.PrivateMessageCommittedEvent;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEvent;
import com.nowcoder.community.im.common.event.PrivateMessageRejectedEvent;
import com.nowcoder.community.im.common.event.RoomMemberChanged;
import com.nowcoder.community.im.common.event.RoomMessageCommittedEvent;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import com.nowcoder.community.im.common.event.RoomMessageRejectedEvent;
import com.nowcoder.community.im.common.event.UserBlockRelationChanged;
import com.nowcoder.community.im.common.event.UserMessagingPolicyChanged;
import com.nowcoder.community.im.realtime.presence.ConnectionRegistry;
import com.nowcoder.community.im.realtime.presence.RoomLocalIndex;
import com.nowcoder.community.im.realtime.projection.MembershipProjectionService;
import com.nowcoder.community.im.realtime.projection.PolicyProjectionService;
import com.nowcoder.community.im.realtime.push.PrivatePushService;
import com.nowcoder.community.im.realtime.push.RoomFanoutCoalescer;
import com.nowcoder.community.im.realtime.push.SendResultPushService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EventConsumers {

    private final PrivatePushService privatePushService;
    private final MembershipProjectionService membershipProjectionService;
    private final PolicyProjectionService policyProjectionService;
    private final ConnectionRegistry connectionRegistry;
    private final RoomLocalIndex roomLocalIndex;
    private final RoomFanoutCoalescer roomFanoutCoalescer;
    private final SendResultPushService sendResultPushService;

    public EventConsumers(
            PrivatePushService privatePushService,
            MembershipProjectionService membershipProjectionService,
            PolicyProjectionService policyProjectionService,
            ConnectionRegistry connectionRegistry,
            RoomLocalIndex roomLocalIndex,
            RoomFanoutCoalescer roomFanoutCoalescer,
            SendResultPushService sendResultPushService
    ) {
        this.privatePushService = privatePushService;
        this.membershipProjectionService = membershipProjectionService;
        this.policyProjectionService = policyProjectionService;
        this.connectionRegistry = connectionRegistry;
        this.roomLocalIndex = roomLocalIndex;
        this.roomFanoutCoalescer = roomFanoutCoalescer;
        this.sendResultPushService = sendResultPushService;
    }

    @KafkaListener(
            topics = "${im.kafka.topics.event-private-persisted:im.event.private-persisted}",
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${im.kafka.event.concurrency:3}"
    )
    public void onPrivatePersisted(PrivateMessagePersistedEvent event) {
        privatePushService.pushPrivateMessage(event);
    }

    @KafkaListener(
            topics = "${im.kafka.topics.event-room-persisted:im.event.room-persisted}",
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${im.kafka.event.concurrency:3}"
    )
    public void onRoomPersisted(RoomMessagePersistedEvent event) {
        if (event == null) {
            return;
        }
        roomFanoutCoalescer.markRoomUpdated(event.roomId(), event.seq());
    }

    @KafkaListener(
            topics = "${im.kafka.topics.event-private-committed:im.event.private-committed}",
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${im.kafka.event.concurrency:3}"
    )
    public void onPrivateCommitted(PrivateMessageCommittedEvent event) {
        sendResultPushService.pushPrivateCommitted(event);
    }

    @KafkaListener(
            topics = "${im.kafka.topics.event-room-committed:im.event.room-committed}",
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${im.kafka.event.concurrency:3}"
    )
    public void onRoomCommitted(RoomMessageCommittedEvent event) {
        sendResultPushService.pushRoomCommitted(event);
    }

    @KafkaListener(
            topics = "${im.kafka.topics.event-private-rejected:im.event.private-rejected}",
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${im.kafka.event.concurrency:3}"
    )
    public void onPrivateRejected(PrivateMessageRejectedEvent event) {
        sendResultPushService.pushPrivateRejected(event);
    }

    @KafkaListener(
            topics = "${im.kafka.topics.event-room-rejected:im.event.room-rejected}",
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${im.kafka.event.concurrency:3}"
    )
    public void onRoomRejected(RoomMessageRejectedEvent event) {
        sendResultPushService.pushRoomRejected(event);
    }

    @KafkaListener(
            topics = "${im.kafka.topics.event-room-member-changed:im.event.room-member-changed}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onRoomMemberChanged(RoomMemberChanged event) {
        if (event == null) {
            return;
        }
        boolean applied = membershipProjectionService.applyRoomMemberChanged(event);
        if (!applied) {
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

    @KafkaListener(
            topics = "${im.kafka.topics.event-user-messaging-policy-changed:im.event.user-messaging-policy-changed}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onUserMessagingPolicyChanged(UserMessagingPolicyChanged event) {
        policyProjectionService.applyUserMessagingPolicyChanged(event);
    }

    @KafkaListener(
            topics = "${im.kafka.topics.event-user-block-relation-changed:im.event.user-block-relation-changed}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onUserBlockRelationChanged(UserBlockRelationChanged event) {
        policyProjectionService.applyUserBlockRelationChanged(event);
    }
}
