package com.nowcoder.community.user.application;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.user.contracts.event.UserContractEvent;
import com.nowcoder.community.user.contracts.event.UserEventTypes;
import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class UserEventDispatchApplicationServiceTest {

    private static final String KAFKA_TOPIC = "custom.user.events";

    private final JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());
    private final UserEventKafkaDispatchPort dispatchPort = mock(UserEventKafkaDispatchPort.class);
    private final UserEventDispatchApplicationService service =
            new UserEventDispatchApplicationService(jsonCodec, dispatchPort, KAFKA_TOPIC);

    @Test
    void serviceShouldOnlyLoadForUserOutboxKafkaPublisher() {
        ConditionalOnExpression conditional = UserEventDispatchApplicationService.class.getAnnotation(ConditionalOnExpression.class);

        assertThat(conditional).isNotNull();
        assertThat(conditional.value()).isEqualTo(
                "'${user.events.publisher:outbox-kafka}' == 'outbox-kafka' && '${events.outbox.enabled:true}' == 'true'"
        );
    }

    @Test
    void dispatchShouldConvertPolicyPayloadAndSendThroughPort() {
        UUID userId = uuid(101);

        service.dispatch(userId.toString(), toJson(new UserContractEvent(
                "user:UserPolicyChanged:" + userId + ":42",
                UserEventTypes.USER_POLICY_CHANGED,
                policyPayload(userId)
        )));

        ArgumentCaptor<UserContractEvent> eventCaptor = ArgumentCaptor.forClass(UserContractEvent.class);
        verify(dispatchPort).send(eq(KAFKA_TOPIC), eq(userId.toString()), eventCaptor.capture());
        UserContractEvent event = eventCaptor.getValue();
        assertThat(event.eventId()).isEqualTo("user:UserPolicyChanged:" + userId + ":42");
        assertThat(event.type()).isEqualTo(UserEventTypes.USER_POLICY_CHANGED);
        assertThat(event.payload()).isInstanceOf(UserPolicyChangedPayload.class);
        UserPolicyChangedPayload payload = (UserPolicyChangedPayload) event.payload();
        assertThat(payload.getUserId()).isEqualTo(userId);
        assertThat(payload.getVersion()).isEqualTo(42L);
        assertThat(payload.isMuted()).isTrue();
    }

    @Test
    void dispatchShouldConvertUnknownPayloadAsObject() {
        service.dispatch("unknown-key", "{\"eventId\":\"user:Unknown:1\",\"type\":\"UnknownUserEvent\",\"payload\":{\"value\":\"kept\"}}");

        ArgumentCaptor<UserContractEvent> eventCaptor = ArgumentCaptor.forClass(UserContractEvent.class);
        verify(dispatchPort).send(eq(KAFKA_TOPIC), eq("unknown-key"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().payload())
                .isInstanceOf(Map.class)
                .extracting(payload -> ((Map<?, ?>) payload).get("value"))
                .isEqualTo("kept");
    }

    @Test
    void dispatchShouldRejectBlankOrNullPayloadForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatch("key", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user event outbox payload is blank");
        assertThatThrownBy(() -> service.dispatch("key", " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user event outbox payload is blank");
    }

    @Test
    void dispatchShouldRejectPayloadMissingEventIdForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatch("key", "{\"type\":\"UserPolicyChanged\",\"payload\":{\"userId\":\"" + uuid(201) + "\"}}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user event outbox payload missing eventId");
        assertThatThrownBy(() -> service.dispatch("key", "{\"eventId\":\" \",\"type\":\"UserPolicyChanged\",\"payload\":{\"userId\":\"" + uuid(201) + "\"}}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user event outbox payload missing eventId");

        verifyNoInteractions(dispatchPort);
    }

    @Test
    void dispatchShouldRejectPayloadMissingTypeForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatch("key", "{\"eventId\":\"event-1\",\"payload\":{\"userId\":\"" + uuid(202) + "\"}}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user event outbox payload missing type");
        assertThatThrownBy(() -> service.dispatch("key", "{\"eventId\":\"event-1\",\"type\":\" \",\"payload\":{\"userId\":\"" + uuid(202) + "\"}}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user event outbox payload missing type");

        verifyNoInteractions(dispatchPort);
    }

    @Test
    void dispatchShouldRejectKnownUserTypeMissingOrNullPayloadForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatch("key", "{\"eventId\":\"event-1\",\"type\":\"UserPolicyChanged\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user event outbox payload missing payload");
        assertThatThrownBy(() -> service.dispatch("key", "{\"eventId\":\"event-2\",\"type\":\"UserPolicyChanged\",\"payload\":null}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user event outbox payload missing payload");

        verifyNoInteractions(dispatchPort);
    }

    @Test
    void dispatchShouldWrapPayloadDeserializationFailure() {
        assertThatThrownBy(() -> service.dispatch("key", "{not-json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user event outbox payload deserialization failed");
    }

    @Test
    void dispatchShouldPropagatePortFailureForOutboxRetry() {
        RuntimeException failure = new RuntimeException("kafka down");
        UUID userId = uuid(301);
        String payloadJson = toJson(new UserContractEvent(
                "user:UserPolicyChanged:" + userId + ":42",
                UserEventTypes.USER_POLICY_CHANGED,
                policyPayload(userId)
        ));
        doThrow(failure).when(dispatchPort).send(eq(KAFKA_TOPIC), eq(userId.toString()), any());

        assertThatThrownBy(() -> service.dispatch(userId.toString(), payloadJson))
                .isSameAs(failure);
    }

    private String toJson(UserContractEvent event) {
        return jsonCodec.toJson(event);
    }

    private static UserPolicyChangedPayload policyPayload(UUID userId) {
        UserPolicyChangedPayload payload = new UserPolicyChangedPayload();
        payload.setUserId(userId);
        payload.setUserExists(true);
        payload.setMuted(true);
        payload.setSuspended(false);
        payload.setMuteUntil(1712345678901L);
        payload.setBanUntil(null);
        payload.setCanSendPrivate(false);
        payload.setOccurredAtEpochMillis(1712345678900L);
        payload.setVersion(42L);
        return payload;
    }
}
