package com.nowcoder.community.im.core.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.im.common.ImTopics;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEvent;
import com.nowcoder.community.im.common.event.PrivateMessageRejectedEvent;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import com.nowcoder.community.im.common.event.RoomMessageRejectedEvent;
import com.nowcoder.community.im.common.event.RoomMemberChanged;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
public class ImMessageOutboxEnqueuer {

    private static final String PRIVATE_PERSISTED_SUFFIX = ":private_persisted";
    private static final String ROOM_PERSISTED_SUFFIX = ":room_persisted";
    private static final String PRIVATE_REJECTED_SUFFIX = ":private_rejected";
    private static final String ROOM_REJECTED_SUFFIX = ":room_rejected";

    private final JdbcOutboxEventStore store;
    private final ObjectMapper objectMapper;

    public ImMessageOutboxEnqueuer(JdbcOutboxEventStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueuePrivatePersisted(PrivateMessagePersistedEvent event) {
        if (event == null) {
            return;
        }
        enqueue(
                outboxEventId(event.requestId(), PRIVATE_PERSISTED_SUFFIX),
                ImTopics.EVENT_PRIVATE_PERSISTED,
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
                outboxEventId(event.requestId(), ROOM_PERSISTED_SUFFIX),
                ImTopics.EVENT_ROOM_PERSISTED,
                String.valueOf(event.roomId()),
                event
        );
    }

    @Transactional
    public void enqueuePrivateRejected(PrivateMessageRejectedEvent event) {
        if (event == null) {
            return;
        }
        enqueue(
                outboxEventId(event.requestId(), PRIVATE_REJECTED_SUFFIX),
                ImTopics.EVENT_PRIVATE_REJECTED,
                event.conversationId(),
                event
        );
    }

    @Transactional
    public void enqueueRoomRejected(RoomMessageRejectedEvent event) {
        if (event == null) {
            return;
        }
        enqueue(
                outboxEventId(event.requestId(), ROOM_REJECTED_SUFFIX),
                ImTopics.EVENT_ROOM_REJECTED,
                String.valueOf(event.roomId()),
                event
        );
    }

    @Transactional
    public void enqueueRoomMemberChanged(RoomMemberChanged event) {
        if (event == null) {
            return;
        }
        enqueue(
                requiredEventId(event.eventId()),
                ImTopics.EVENT_ROOM_MEMBER_CHANGED,
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

    private String outboxEventId(String requestId, String suffix) {
        if (!StringUtils.hasText(requestId)) {
            throw new IllegalArgumentException("requestId required for IM outbox event");
        }
        return requestId.trim() + suffix;
    }

    private String requiredEventId(String eventId) {
        if (!StringUtils.hasText(eventId)) {
            throw new IllegalArgumentException("eventId required for IM outbox event");
        }
        return eventId.trim();
    }
}
