package com.nowcoder.community.user.infrastructure.event;

import com.nowcoder.community.user.contracts.event.UserContractEvent;
import com.nowcoder.community.user.contracts.event.UserEventTypes;
import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;
import com.nowcoder.community.user.domain.model.UserModerationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LocalUserPolicyEventPublisherTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000007");

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Test
    void publishUserPolicyChangedShouldEmitUserPolicyChangedPayload() {
        LocalUserPolicyEventPublisher publisher = new LocalUserPolicyEventPublisher(applicationEventPublisher);
        Instant occurredAt = Instant.parse("2026-04-28T01:00:00Z");
        Instant muteUntil = occurredAt.plusSeconds(60);

        publisher.publishUserPolicyChanged(new UserModerationStatus(USER_ID, muteUntil, null), occurredAt);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(UserContractEvent.class);
        UserContractEvent event = (UserContractEvent) eventCaptor.getValue();
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.type()).isEqualTo(UserEventTypes.USER_POLICY_CHANGED);
        assertThat(event.payload()).isInstanceOf(UserPolicyChangedPayload.class);

        UserPolicyChangedPayload payload = (UserPolicyChangedPayload) event.payload();
        assertThat(payload.getUserId()).isEqualTo(USER_ID);
        assertThat(payload.isUserExists()).isTrue();
        assertThat(payload.isMuted()).isTrue();
        assertThat(payload.isSuspended()).isFalse();
        assertThat(payload.getMuteUntil()).isEqualTo(muteUntil.toEpochMilli());
        assertThat(payload.getBanUntil()).isNull();
        assertThat(payload.isCanSendPrivate()).isFalse();
        assertThat(payload.getOccurredAtEpochMillis()).isEqualTo(occurredAt.toEpochMilli());
    }
}
