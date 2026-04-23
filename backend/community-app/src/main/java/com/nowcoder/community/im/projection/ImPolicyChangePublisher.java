package com.nowcoder.community.im.projection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;
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

    public void publishUserPolicyChanged(UserPolicyChangedPayload payload) {
        if (payload == null) {
            return;
        }
        enqueue(new Payload(
                "USER_POLICY",
                payload.getUserId(),
                null,
                null,
                payload.isUserExists(),
                payload.isSuspended(),
                payload.isMuted(),
                payload.getMuteUntil(),
                payload.getBanUntil(),
                payload.isCanSendPrivate(),
                payload.getOccurredAtEpochMillis()
        ));
    }

    public void publishBlockRelationChanged(UUID blockerUserId, UUID blockedUserId, boolean active) {
        enqueue(new Payload(
                "BLOCK",
                blockerUserId,
                blockedUserId,
                active,
                false,
                false,
                false,
                null,
                null,
                false,
                System.currentTimeMillis()
        ));
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

    record Payload(
            String kind,
            UUID primaryUserId,
            UUID secondaryUserId,
            Boolean active,
            boolean userExists,
            boolean suspended,
            boolean muted,
            Long muteUntil,
            Long banUntil,
            boolean canSendPrivate,
            long occurredAtEpochMillis
    ) {
    }
}
