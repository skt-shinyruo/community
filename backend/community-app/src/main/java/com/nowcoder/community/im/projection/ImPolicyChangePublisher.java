package com.nowcoder.community.im.projection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class ImPolicyChangePublisher {

    static final String TOPIC = "projection.im.policy";

    private final JdbcOutboxEventStore store;
    private final ObjectMapper objectMapper;
    private final UuidV7Generator idGenerator = new UuidV7Generator();

    public ImPolicyChangePublisher(JdbcOutboxEventStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public void publishUserPolicyChanged(UUID userId) {
        enqueue(new Payload("MODERATION", userId, null, null));
    }

    public void publishBlockRelationChanged(UUID blockerUserId, UUID blockedUserId, boolean active) {
        enqueue(new Payload("BLOCK", blockerUserId, blockedUserId, active));
    }

    private void enqueue(Payload payload) {
        if (payload == null || payload.primaryUserId() == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            String eventId = buildEventId(payload);
            store.enqueue(eventId, TOPIC, String.valueOf(payload.primaryUserId()), json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("im policy outbox payload 序列化失败", e);
        }
    }

    private String buildEventId(Payload payload) {
        return "im-policy:" + payload.kind() + ":" + idGenerator.next();
    }

    record Payload(String kind, UUID primaryUserId, UUID secondaryUserId, Boolean active) {
    }
}
