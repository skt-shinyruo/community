package com.nowcoder.community.user.infrastructure.event;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.user.contracts.event.UserContractEvent;
import com.nowcoder.community.user.contracts.event.UserEventTypes;
import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;
import com.nowcoder.community.user.domain.event.UserPolicyEventPublisher;
import com.nowcoder.community.user.domain.model.UserModerationStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnExpression("'${user.events.publisher:outbox-kafka}' == 'outbox-kafka' && '${events.outbox.enabled:true}' == 'true'")
public class OutboxUserPolicyEventPublisher implements UserPolicyEventPublisher {

    private final JsonCodec jsonCodec;
    private final JdbcOutboxEventStore store;
    private final String topic;

    public OutboxUserPolicyEventPublisher(
            JsonCodec jsonCodec,
            JdbcOutboxEventStore store,
            @Value("${user.events.outbox-topic:eventbus.user}") String topic
    ) {
        this.jsonCodec = jsonCodec;
        this.store = store;
        this.topic = topic;
    }

    @Override
    public void publishUserPolicyChanged(UserModerationStatus status, Instant occurredAt) {
        UUID userId = status == null ? null : status.userId();
        if (userId == null) {
            return;
        }
        Instant occurrence = occurredAt == null ? Instant.now() : occurredAt;
        Long muteUntil = status.muteUntil() == null ? null : status.muteUntil().toEpochMilli();
        Long banUntil = status.banUntil() == null ? null : status.banUntil().toEpochMilli();
        publish(toPayload(userId, true, muteUntil, banUntil, occurrence, status.version()));
    }

    @Override
    public void publishUserPolicyChanged(UUID userId, boolean userExists, Instant occurredAt, long version) {
        if (userId == null) {
            return;
        }
        Instant occurrence = occurredAt == null ? Instant.now() : occurredAt;
        publish(toPayload(userId, userExists, null, null, occurrence, version));
    }

    private UserPolicyChangedPayload toPayload(
            UUID userId,
            boolean userExists,
            Long muteUntil,
            Long banUntil,
            Instant occurrence,
            long version
    ) {
        boolean suspended = banUntil != null && Instant.ofEpochMilli(banUntil).isAfter(occurrence);
        boolean muted = muteUntil != null && Instant.ofEpochMilli(muteUntil).isAfter(occurrence);

        UserPolicyChangedPayload payload = new UserPolicyChangedPayload();
        payload.setUserId(userId);
        payload.setUserExists(userExists);
        payload.setSuspended(suspended);
        payload.setMuted(muted);
        payload.setMuteUntil(muteUntil);
        payload.setBanUntil(banUntil);
        payload.setCanSendPrivate(payload.isUserExists() && !suspended && !muted);
        payload.setOccurredAtEpochMillis(occurrence.toEpochMilli());
        payload.setVersion(version);
        return payload;
    }

    private void publish(UserPolicyChangedPayload payload) {
        String eventId = "ue:p:" + dashless(payload.getUserId()) + ":" + versionOrZero(payload.getVersion());
        String payloadJson;
        try {
            payloadJson = jsonCodec.toJson(new UserContractEvent(eventId, UserEventTypes.USER_POLICY_CHANGED, payload));
        } catch (JsonCodecException e) {
            throw new IllegalStateException("user event outbox payload serialization failed", e);
        }
        store.enqueue(eventId, topic, payload.getUserId().toString(), payloadJson);
    }

    private long versionOrZero(Long version) {
        return version == null ? 0L : version;
    }

    private String dashless(UUID userId) {
        return userId.toString().replace("-", "");
    }
}
