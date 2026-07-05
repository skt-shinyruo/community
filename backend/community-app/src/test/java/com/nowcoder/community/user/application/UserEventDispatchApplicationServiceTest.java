package com.nowcoder.community.user.application;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.user.application.command.DispatchUserEventCommand;
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

    private final JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());
    private final UserIntegrationEventDispatcher dispatcher = mock(UserIntegrationEventDispatcher.class);
    private final UserEventDispatchApplicationService service =
            new UserEventDispatchApplicationService(jsonCodec, dispatcher);

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

        service.dispatch(new DispatchUserEventCommand(userId.toString(), toJson(new UserContractEvent(
                "user:UserPolicyChanged:" + userId + ":42",
                UserEventTypes.USER_POLICY_CHANGED,
                policyPayload(userId)
        ))));

        ArgumentCaptor<UserContractEvent> eventCaptor = ArgumentCaptor.forClass(UserContractEvent.class);
        verify(dispatcher).dispatch(eq(userId.toString()), eventCaptor.capture());
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
        service.dispatch(new DispatchUserEventCommand(
                "unknown-key",
                "{\"eventId\":\"user:Unknown:1\",\"type\":\"UnknownUserEvent\",\"payload\":{\"value\":\"kept\"}}"
        ));

        ArgumentCaptor<UserContractEvent> eventCaptor = ArgumentCaptor.forClass(UserContractEvent.class);
        verify(dispatcher).dispatch(eq("unknown-key"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().payload())
                .isInstanceOf(Map.class)
                .extracting(payload -> ((Map<?, ?>) payload).get("value"))
                .isEqualTo("kept");
    }

    @Test
    void dispatchShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.dispatch(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void dispatchShouldRejectBlankOrNullPayloadForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatch(new DispatchUserEventCommand("key", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user event outbox payload is blank");
        assertThatThrownBy(() -> service.dispatch(new DispatchUserEventCommand("key", " ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user event outbox payload is blank");
    }

    @Test
    void dispatchShouldRejectPayloadMissingEventIdForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatch(new DispatchUserEventCommand(
                "key",
                "{\"type\":\"UserPolicyChanged\",\"payload\":{\"userId\":\"" + uuid(201) + "\"}}"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user event outbox payload missing eventId");
        assertThatThrownBy(() -> service.dispatch(new DispatchUserEventCommand(
                "key",
                "{\"eventId\":\" \",\"type\":\"UserPolicyChanged\",\"payload\":{\"userId\":\"" + uuid(201) + "\"}}"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user event outbox payload missing eventId");

        verifyNoInteractions(dispatcher);
    }

    @Test
    void dispatchShouldRejectPayloadMissingTypeForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatch(new DispatchUserEventCommand(
                "key",
                "{\"eventId\":\"event-1\",\"payload\":{\"userId\":\"" + uuid(202) + "\"}}"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user event outbox payload missing type");
        assertThatThrownBy(() -> service.dispatch(new DispatchUserEventCommand(
                "key",
                "{\"eventId\":\"event-1\",\"type\":\" \",\"payload\":{\"userId\":\"" + uuid(202) + "\"}}"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user event outbox payload missing type");

        verifyNoInteractions(dispatcher);
    }

    @Test
    void dispatchShouldRejectKnownUserTypeMissingOrNullPayloadForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatch(new DispatchUserEventCommand("key", "{\"eventId\":\"event-1\",\"type\":\"UserPolicyChanged\"}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user event outbox payload missing payload");
        assertThatThrownBy(() -> service.dispatch(new DispatchUserEventCommand("key", "{\"eventId\":\"event-2\",\"type\":\"UserPolicyChanged\",\"payload\":null}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user event outbox payload missing payload");

        verifyNoInteractions(dispatcher);
    }

    @Test
    void dispatchShouldWrapPayloadDeserializationFailure() {
        assertThatThrownBy(() -> service.dispatch(new DispatchUserEventCommand("key", "{not-json")))
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
        doThrow(failure).when(dispatcher).dispatch(eq(userId.toString()), any());

        assertThatThrownBy(() -> service.dispatch(new DispatchUserEventCommand(userId.toString(), payloadJson)))
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
