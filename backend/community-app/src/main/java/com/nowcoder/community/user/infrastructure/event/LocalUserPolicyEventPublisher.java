package com.nowcoder.community.user.infrastructure.event;

import com.nowcoder.community.user.contracts.event.UserContractEvent;
import com.nowcoder.community.user.contracts.event.UserEventTypes;
import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;
import com.nowcoder.community.user.domain.event.UserPolicyEventPublisher;
import com.nowcoder.community.user.domain.model.UserModerationStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "user.events.publisher", havingValue = "local")
public class LocalUserPolicyEventPublisher implements UserPolicyEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public LocalUserPolicyEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publishUserPolicyChanged(UserModerationStatus status, Instant occurredAt) {
        Instant occurrence = occurredAt == null ? Instant.now() : occurredAt;
        applicationEventPublisher.publishEvent(new UserContractEvent(
                UUID.randomUUID().toString(),
                UserEventTypes.USER_POLICY_CHANGED,
                toPayload(status, occurrence)
        ));
    }

    @Override
    public void publishUserPolicyChanged(UUID userId, boolean userExists, Instant occurredAt, long version) {
        Instant occurrence = occurredAt == null ? Instant.now() : occurredAt;
        applicationEventPublisher.publishEvent(new UserContractEvent(
                UUID.randomUUID().toString(),
                UserEventTypes.USER_POLICY_CHANGED,
                toPayload(userId, userExists, null, null, occurrence, version)
        ));
    }

    private UserPolicyChangedPayload toPayload(UserModerationStatus status, Instant occurrence) {
        Long muteUntil = status == null || status.muteUntil() == null ? null : status.muteUntil().toEpochMilli();
        Long banUntil = status == null || status.banUntil() == null ? null : status.banUntil().toEpochMilli();
        return toPayload(
                status == null ? null : status.userId(),
                status != null && status.userId() != null,
                muteUntil,
                banUntil,
                occurrence,
                status == null ? 0L : status.version()
        );
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
}
