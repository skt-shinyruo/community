package com.nowcoder.community.im.core.outbox;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.im.common.event.ImEventIds;
import com.nowcoder.community.im.common.event.PrivateMessageCommittedEvent;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEvent;
import com.nowcoder.community.im.common.event.PrivateMessageRejectedEvent;
import com.nowcoder.community.im.common.event.RoomMessageCommittedEvent;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import com.nowcoder.community.im.common.event.RoomMessageRejectedEvent;
import com.nowcoder.community.im.common.event.RoomMemberChanged;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
public class ImMessageOutboxEnqueuer {

    private final JdbcOutboxEventStore store;
    private final JsonCodec jsonCodec;
    private final String privatePersistedTopic;
    private final String roomPersistedTopic;
    private final String privateCommittedTopic;
    private final String roomCommittedTopic;
    private final String privateRejectedTopic;
    private final String roomRejectedTopic;
    private final String roomMemberChangedTopic;

    public ImMessageOutboxEnqueuer(
            JdbcOutboxEventStore store,
            JsonCodec jsonCodec,
            @Value("${im.kafka.topics.event-private-persisted:im.event.private-persisted}") String privatePersistedTopic,
            @Value("${im.kafka.topics.event-room-persisted:im.event.room-persisted}") String roomPersistedTopic,
            @Value("${im.kafka.topics.event-private-committed:im.event.private-committed}") String privateCommittedTopic,
            @Value("${im.kafka.topics.event-room-committed:im.event.room-committed}") String roomCommittedTopic,
            @Value("${im.kafka.topics.event-private-rejected:im.event.private-rejected}") String privateRejectedTopic,
            @Value("${im.kafka.topics.event-room-rejected:im.event.room-rejected}") String roomRejectedTopic,
            @Value("${im.kafka.topics.event-room-member-changed:im.event.room-member-changed}") String roomMemberChangedTopic
    ) {
        this.store = store;
        this.jsonCodec = jsonCodec;
        this.privatePersistedTopic = privatePersistedTopic;
        this.roomPersistedTopic = roomPersistedTopic;
        this.privateCommittedTopic = privateCommittedTopic;
        this.roomCommittedTopic = roomCommittedTopic;
        this.privateRejectedTopic = privateRejectedTopic;
        this.roomRejectedTopic = roomRejectedTopic;
        this.roomMemberChangedTopic = roomMemberChangedTopic;
    }

    @Transactional
    public void enqueuePrivatePersisted(PrivateMessagePersistedEvent event) {
        if (event == null) {
            return;
        }
        enqueue(
                requiredEventId(event.eventId()),
                privatePersistedTopic,
                event.conversationId(),
                event
        );
    }

    @Transactional
    public void enqueueRoomPersisted(RoomMessagePersistedEvent event) {
        if (event == null) {
            return;
        }
        enqueue(
                requiredEventId(event.eventId()),
                roomPersistedTopic,
                String.valueOf(event.roomId()),
                event
        );
    }

    @Transactional
    public void enqueuePrivateCommitted(PrivateMessageCommittedEvent event) {
        if (event == null) {
            return;
        }
        PrivateMessageCommittedEvent normalized = requiredCanonicalPrivateCommitted(event);
        enqueue(
                normalized.eventId(),
                privateCommittedTopic,
                normalized.conversationId(),
                normalized
        );
    }

    @Transactional
    public void enqueueRoomCommitted(RoomMessageCommittedEvent event) {
        if (event == null) {
            return;
        }
        RoomMessageCommittedEvent normalized = requiredCanonicalRoomCommitted(event);
        enqueue(
                normalized.eventId(),
                roomCommittedTopic,
                String.valueOf(normalized.roomId()),
                normalized
        );
    }

    @Transactional
    public void enqueuePrivateRejected(PrivateMessageRejectedEvent event) {
        if (event == null) {
            return;
        }
        PrivateMessageRejectedEvent normalized = requiredCanonicalPrivateRejected(event);
        enqueue(
                normalized.eventId(),
                privateRejectedTopic,
                normalized.conversationId(),
                normalized
        );
    }

    @Transactional
    public void enqueueRoomRejected(RoomMessageRejectedEvent event) {
        if (event == null) {
            return;
        }
        RoomMessageRejectedEvent normalized = requiredCanonicalRoomRejected(event);
        enqueue(
                normalized.eventId(),
                roomRejectedTopic,
                String.valueOf(normalized.roomId()),
                normalized
        );
    }

    @Transactional
    public void enqueueRoomMemberChanged(RoomMemberChanged event) {
        if (event == null) {
            return;
        }
        enqueue(
                requiredEventId(event.eventId()),
                roomMemberChangedTopic,
                String.valueOf(event.roomId()),
                event
        );
    }

    private void enqueue(String eventId, String topic, String eventKey, Object payload) {
        try {
            store.enqueue(eventId, topic, eventKey, jsonCodec.toJson(payload));
        } catch (JsonCodecException e) {
            throw new IllegalStateException("IM outbox payload serialization failed: " + eventId, e);
        }
    }

    private String requiredEventId(String eventId) {
        if (!StringUtils.hasText(eventId)) {
            throw new IllegalArgumentException("eventId required for IM outbox event");
        }
        return eventId.trim();
    }

    private PrivateMessageCommittedEvent requiredCanonicalPrivateCommitted(PrivateMessageCommittedEvent event) {
        requireCanonicalEventId(
                event.eventId(),
                ImEventIds.privateSendResult(event.requestId(), event.clientMsgId(), event.fromUserId())
        );
        return event;
    }

    private RoomMessageCommittedEvent requiredCanonicalRoomCommitted(RoomMessageCommittedEvent event) {
        requireCanonicalEventId(
                event.eventId(),
                ImEventIds.roomSendResult(event.requestId(), event.clientMsgId(), event.fromUserId())
        );
        return event;
    }

    private PrivateMessageRejectedEvent requiredCanonicalPrivateRejected(PrivateMessageRejectedEvent event) {
        requireCanonicalEventId(
                event.eventId(),
                ImEventIds.privateSendResult(event.requestId(), event.clientMsgId(), event.fromUserId())
        );
        return event;
    }

    private RoomMessageRejectedEvent requiredCanonicalRoomRejected(RoomMessageRejectedEvent event) {
        requireCanonicalEventId(
                event.eventId(),
                ImEventIds.roomSendResult(event.requestId(), event.clientMsgId(), event.fromUserId())
        );
        return event;
    }

    private void requireCanonicalEventId(String actualEventId, String expectedEventId) {
        if (!expectedEventId.equals(actualEventId)) {
            throw new IllegalArgumentException(
                    "non-canonical IM eventId: expected=" + expectedEventId + ", actual=" + actualEventId);
        }
    }
}
