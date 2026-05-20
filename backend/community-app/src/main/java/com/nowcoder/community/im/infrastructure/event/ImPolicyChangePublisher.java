package com.nowcoder.community.im.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class ImPolicyChangePublisher {

    private final JdbcOutboxEventStore store;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final UuidV7Generator idGenerator = new UuidV7Generator();

    public ImPolicyChangePublisher(
            JdbcOutboxEventStore store,
            ObjectMapper objectMapper,
            @Value("${im.policy.outbox.topic:projection.im.policy}") String topic
    ) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void publishUserPolicyChanged(UserPolicyChangedPayload payload) {
        if (payload == null) {
            return;
        }
        long occurredAtEpochMillis = payload.getOccurredAtEpochMillis();
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
                occurredAtEpochMillis,
                payload.getVersion() == null ? 0L : payload.getVersion()
        ));
    }

    public void publishBlockRelationChanged(UUID blockerUserId, UUID blockedUserId, boolean active, long version) {
        long occurredAtEpochMillis = System.currentTimeMillis();
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
                occurredAtEpochMillis,
                version
        ));
    }

    private void enqueue(Payload payload) {
        if (payload == null || payload.primaryUserId() == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            String eventId = buildEventId(payload);
            store.enqueue(eventId, topic, String.valueOf(payload.primaryUserId()), json);
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
            long occurredAtEpochMillis,
            long version
    ) {
    }
}
