package com.nowcoder.community.im.core.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEvent;
import com.nowcoder.community.im.common.event.PrivateMessageRejectedEvent;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import com.nowcoder.community.im.common.event.RoomMessageRejectedEvent;
import com.nowcoder.community.im.common.event.RoomMemberChanged;
import org.springframework.beans.factory.annotation.Value;
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
    private final String privatePersistedTopic;
    private final String roomPersistedTopic;
    private final String privateRejectedTopic;
    private final String roomRejectedTopic;
    private final String roomMemberChangedTopic;

    public ImMessageOutboxEnqueuer(
            JdbcOutboxEventStore store,
            ObjectMapper objectMapper,
            @Value("${im.kafka.topics.event-private-persisted:im.event.private-persisted}") String privatePersistedTopic,
            @Value("${im.kafka.topics.event-room-persisted:im.event.room-persisted}") String roomPersistedTopic,
            @Value("${im.kafka.topics.event-private-rejected:im.event.private-rejected}") String privateRejectedTopic,
            @Value("${im.kafka.topics.event-room-rejected:im.event.room-rejected}") String roomRejectedTopic,
            @Value("${im.kafka.topics.event-room-member-changed:im.event.room-member-changed}") String roomMemberChangedTopic
    ) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.privatePersistedTopic = privatePersistedTopic;
        this.roomPersistedTopic = roomPersistedTopic;
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
                outboxEventId(event.requestId(), PRIVATE_PERSISTED_SUFFIX),
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
                outboxEventId(event.requestId(), ROOM_PERSISTED_SUFFIX),
                roomPersistedTopic,
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
                privateRejectedTopic,
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
                roomRejectedTopic,
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
