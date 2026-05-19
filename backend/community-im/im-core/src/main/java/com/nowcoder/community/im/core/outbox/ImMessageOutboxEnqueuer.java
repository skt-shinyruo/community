package com.nowcoder.community.im.core.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;
    private final String privatePersistedTopic;
    private final String roomPersistedTopic;
    private final String privateCommittedTopic;
    private final String roomCommittedTopic;
    private final String privateRejectedTopic;
    private final String roomRejectedTopic;
    private final String roomMemberChangedTopic;

    public ImMessageOutboxEnqueuer(
            JdbcOutboxEventStore store,
            ObjectMapper objectMapper,
            @Value("${im.kafka.topics.event-private-persisted:im.event.private-persisted}") String privatePersistedTopic,
            @Value("${im.kafka.topics.event-room-persisted:im.event.room-persisted}") String roomPersistedTopic,
            @Value("${im.kafka.topics.event-private-committed:im.event.private-committed}") String privateCommittedTopic,
            @Value("${im.kafka.topics.event-room-committed:im.event.room-committed}") String roomCommittedTopic,
            @Value("${im.kafka.topics.event-private-rejected:im.event.private-rejected}") String privateRejectedTopic,
            @Value("${im.kafka.topics.event-room-rejected:im.event.room-rejected}") String roomRejectedTopic,
            @Value("${im.kafka.topics.event-room-member-changed:im.event.room-member-changed}") String roomMemberChangedTopic
    ) {
        this.store = store;
        this.objectMapper = objectMapper;
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
        PrivateMessageCommittedEvent normalized = normalizedPrivateCommitted(event);
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
        RoomMessageCommittedEvent normalized = normalizedRoomCommitted(event);
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
        PrivateMessageRejectedEvent normalized = normalizedPrivateRejected(event);
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
        RoomMessageRejectedEvent normalized = normalizedRoomRejected(event);
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
            store.enqueue(eventId, topic, eventKey, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("IM outbox payload serialization failed: " + eventId, e);
        }
    }

    private String requiredEventId(String eventId) {
        if (!StringUtils.hasText(eventId)) {
            throw new IllegalArgumentException("eventId required for IM outbox event");
        }
        return eventId.trim();
    }

    private PrivateMessageCommittedEvent normalizedPrivateCommitted(PrivateMessageCommittedEvent event) {
        String eventId = ImEventIds.privateSendResult(event.requestId(), event.clientMsgId(), event.fromUserId());
        if (eventId.equals(event.eventId())) {
            return event;
        }
        return new PrivateMessageCommittedEvent(
                eventId,
                event.requestId(),
                event.clientMsgId(),
                event.fromUserId(),
                event.toUserId(),
                event.conversationId(),
                event.messageId(),
                event.seq(),
                event.createdAtEpochMs()
        );
    }

    private RoomMessageCommittedEvent normalizedRoomCommitted(RoomMessageCommittedEvent event) {
        String eventId = ImEventIds.roomSendResult(event.requestId(), event.clientMsgId(), event.fromUserId());
        if (eventId.equals(event.eventId())) {
            return event;
        }
        return new RoomMessageCommittedEvent(
                eventId,
                event.requestId(),
                event.clientMsgId(),
                event.fromUserId(),
                event.roomId(),
                event.messageId(),
                event.seq(),
                event.createdAtEpochMs()
        );
    }

    private PrivateMessageRejectedEvent normalizedPrivateRejected(PrivateMessageRejectedEvent event) {
        String eventId = ImEventIds.privateSendResult(event.requestId(), event.clientMsgId(), event.fromUserId());
        if (eventId.equals(event.eventId())) {
            return event;
        }
        return new PrivateMessageRejectedEvent(
                eventId,
                event.requestId(),
                event.clientMsgId(),
                event.fromUserId(),
                event.toUserId(),
                event.conversationId(),
                event.code(),
                event.reasonCode(),
                event.message(),
                event.createdAtEpochMs()
        );
    }

    private RoomMessageRejectedEvent normalizedRoomRejected(RoomMessageRejectedEvent event) {
        String eventId = ImEventIds.roomSendResult(event.requestId(), event.clientMsgId(), event.fromUserId());
        if (eventId.equals(event.eventId())) {
            return event;
        }
        return new RoomMessageRejectedEvent(
                eventId,
                event.requestId(),
                event.clientMsgId(),
                event.fromUserId(),
                event.roomId(),
                event.code(),
                event.reasonCode(),
                event.message(),
                event.createdAtEpochMs()
        );
    }
}
