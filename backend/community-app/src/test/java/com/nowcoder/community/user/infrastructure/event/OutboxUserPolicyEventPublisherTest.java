package com.nowcoder.community.user.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.user.application.UserEventDispatchApplicationService;
import com.nowcoder.community.user.application.UserIntegrationEventDispatcher;
import com.nowcoder.community.user.application.command.DispatchUserEventCommand;
import com.nowcoder.community.user.contracts.event.UserContractEvent;
import com.nowcoder.community.user.contracts.event.UserEventTypes;
import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;
import com.nowcoder.community.user.domain.event.UserPolicyEventPublisher;
import com.nowcoder.community.user.domain.model.UserModerationStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class OutboxUserPolicyEventPublisherTest {

    private static final String TOPIC = "custom.eventbus.user";

    @Test
    void publisherShouldImplementCanonicalOwnerPort() {
        assertThat(OutboxUserPolicyEventPublisher.class.getInterfaces())
                .containsExactly(UserPolicyEventPublisher.class);
    }

    @Test
    void statusBasedPolicyChangeShouldWriteUserContractEnvelopeToOutbox() throws Exception {
        ObjectMapper objectMapper = JsonMappers.standard();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        UUID userId = uuid(7);
        Instant occurredAt = Instant.parse("2026-04-28T01:00:00Z");
        Instant muteUntil = occurredAt.plusSeconds(60);
        OutboxUserPolicyEventPublisher publisher = new OutboxUserPolicyEventPublisher(
                new JacksonJsonCodec(JsonMappers.standard()),
                store,
                TOPIC
        );

        publisher.publishUserPolicyChanged(new UserModerationStatus(userId, muteUntil, null, 42L), occurredAt);

        String eventId = policyEventId(userId, 42L);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(
                eq(eventId),
                eq(TOPIC),
                eq(userId.toString()),
                payloadCaptor.capture()
        );
        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(eventId.length()).isLessThanOrEqualTo(64);
        assertThat(json.path("eventId").asText()).isEqualTo(eventId);
        assertThat(json.path("type").asText()).isEqualTo(UserEventTypes.USER_POLICY_CHANGED);
        assertThat(json.path("payload").path("userId").asText()).isEqualTo(userId.toString());
        assertThat(json.path("payload").path("version").asLong()).isEqualTo(42L);
        assertThat(json.path("payload").path("muted").asBoolean()).isTrue();
        assertThat(json.path("payload").path("suspended").asBoolean()).isFalse();
        assertThat(json.path("payload").path("canSendPrivate").asBoolean()).isFalse();
    }

    @Test
    void statusBasedPolicyOutboxPayloadShouldDispatchAsTypedKafkaContractEvent() {
        JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        UserIntegrationEventDispatcher dispatcher = mock(UserIntegrationEventDispatcher.class);
        UUID userId = uuid(70);
        Instant occurredAt = Instant.parse("2026-04-28T01:00:00Z");
        Instant muteUntil = occurredAt.plusSeconds(60);
        OutboxUserPolicyEventPublisher publisher = new OutboxUserPolicyEventPublisher(jsonCodec, store, TOPIC);
        UserEventDispatchApplicationService dispatchService =
                new UserEventDispatchApplicationService(jsonCodec, dispatcher);

        publisher.publishUserPolicyChanged(new UserModerationStatus(userId, muteUntil, null, 42L), occurredAt);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(
                eq(policyEventId(userId, 42L)),
                eq(TOPIC),
                eq(userId.toString()),
                payloadCaptor.capture()
        );
        dispatchService.dispatch(new DispatchUserEventCommand(userId.toString(), payloadCaptor.getValue()));

        ArgumentCaptor<UserContractEvent> eventCaptor = ArgumentCaptor.forClass(UserContractEvent.class);
        verify(dispatcher).dispatch(eq(userId.toString()), eventCaptor.capture());
        UserContractEvent event = eventCaptor.getValue();
        assertThat(event.eventId()).isEqualTo(policyEventId(userId, 42L));
        assertThat(event.type()).isEqualTo(UserEventTypes.USER_POLICY_CHANGED);
        assertThat(event.payload()).isInstanceOf(UserPolicyChangedPayload.class);
        UserPolicyChangedPayload payload = (UserPolicyChangedPayload) event.payload();
        assertThat(payload.getUserId()).isEqualTo(userId);
        assertThat(payload.getVersion()).isEqualTo(42L);
        assertThat(payload.isMuted()).isTrue();
        assertThat(payload.isCanSendPrivate()).isFalse();
    }

    @Test
    void userIdPolicyChangeShouldUseVersionOrZeroEventIdAndKey() throws Exception {
        ObjectMapper objectMapper = JsonMappers.standard();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        UUID userId = uuid(8);
        Instant occurredAt = Instant.parse("2026-04-28T02:00:00Z");
        OutboxUserPolicyEventPublisher publisher = new OutboxUserPolicyEventPublisher(
                new JacksonJsonCodec(JsonMappers.standard()),
                store,
                TOPIC
        );

        publisher.publishUserPolicyChanged(userId, false, occurredAt, 0L);

        String eventId = policyEventId(userId, 0L);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(
                eq(eventId),
                eq(TOPIC),
                eq(userId.toString()),
                payloadCaptor.capture()
        );
        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(eventId.length()).isLessThanOrEqualTo(64);
        assertThat(json.path("eventId").asText()).isEqualTo(eventId);
        assertThat(json.path("payload").path("userId").asText()).isEqualTo(userId.toString());
        assertThat(json.path("payload").path("userExists").asBoolean()).isFalse();
        assertThat(json.path("payload").path("canSendPrivate").asBoolean()).isFalse();
        assertThat(json.path("payload").path("version").asLong()).isZero();
    }

    @Test
    void userExistencePolicyOutboxPayloadShouldDispatchAsTypedKafkaContractEvent() {
        JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        UserIntegrationEventDispatcher dispatcher = mock(UserIntegrationEventDispatcher.class);
        UUID userId = uuid(80);
        Instant occurredAt = Instant.parse("2026-04-28T02:00:00Z");
        OutboxUserPolicyEventPublisher publisher = new OutboxUserPolicyEventPublisher(jsonCodec, store, TOPIC);
        UserEventDispatchApplicationService dispatchService =
                new UserEventDispatchApplicationService(jsonCodec, dispatcher);

        publisher.publishUserPolicyChanged(userId, false, occurredAt, 0L);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(
                eq(policyEventId(userId, 0L)),
                eq(TOPIC),
                eq(userId.toString()),
                payloadCaptor.capture()
        );
        dispatchService.dispatch(new DispatchUserEventCommand(userId.toString(), payloadCaptor.getValue()));

        ArgumentCaptor<UserContractEvent> eventCaptor = ArgumentCaptor.forClass(UserContractEvent.class);
        verify(dispatcher).dispatch(eq(userId.toString()), eventCaptor.capture());
        UserContractEvent event = eventCaptor.getValue();
        assertThat(event.eventId()).isEqualTo(policyEventId(userId, 0L));
        assertThat(event.type()).isEqualTo(UserEventTypes.USER_POLICY_CHANGED);
        assertThat(event.payload()).isInstanceOf(UserPolicyChangedPayload.class);
        UserPolicyChangedPayload payload = (UserPolicyChangedPayload) event.payload();
        assertThat(payload.getUserId()).isEqualTo(userId);
        assertThat(payload.isUserExists()).isFalse();
        assertThat(payload.isCanSendPrivate()).isFalse();
        assertThat(payload.getVersion()).isZero();
    }

    @Test
    void userPolicyEventIdShouldFitWithNineteenDigitVersion() {
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        UUID userId = uuid(9);
        OutboxUserPolicyEventPublisher publisher = new OutboxUserPolicyEventPublisher(
                new JacksonJsonCodec(JsonMappers.standard()),
                store,
                TOPIC
        );

        publisher.publishUserPolicyChanged(userId, true, Instant.EPOCH, Long.MAX_VALUE);

        verify(store).enqueue(
                eq(policyEventId(userId, Long.MAX_VALUE)),
                eq(TOPIC),
                eq(userId.toString()),
                org.mockito.ArgumentMatchers.any()
        );
        assertThat(policyEventId(userId, Long.MAX_VALUE).length()).isLessThanOrEqualTo(64);
    }

    @Test
    void eventsWithoutUserIdShouldNotEnqueue() {
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        OutboxUserPolicyEventPublisher publisher = new OutboxUserPolicyEventPublisher(
                new JacksonJsonCodec(JsonMappers.standard()),
                store,
                TOPIC
        );

        publisher.publishUserPolicyChanged((UserModerationStatus) null, Instant.EPOCH);
        publisher.publishUserPolicyChanged(new UserModerationStatus(null, null, null, 0L), Instant.EPOCH);
        publisher.publishUserPolicyChanged(null, true, Instant.EPOCH, 0L);

        verifyNoInteractions(store);
    }

    @Test
    void serializationFailureShouldThrowRetryVisibleException() {
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        JsonCodec failingJsonCodec = new JsonCodec() {
            @Override
            public String toJson(Object value) {
                throw new JsonCodecException("boom", new RuntimeException("boom"));
            }

            @Override
            public <T> T fromJson(String json, Class<T> type) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.fasterxml.jackson.databind.JsonNode readTree(String json) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T treeToValue(com.fasterxml.jackson.databind.JsonNode node, Class<T> type) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.fasterxml.jackson.databind.JsonNode valueToTree(Object value) {
                throw new UnsupportedOperationException();
            }
        };
        OutboxUserPolicyEventPublisher publisher = new OutboxUserPolicyEventPublisher(failingJsonCodec, store, TOPIC);

        assertThatThrownBy(() -> publisher.publishUserPolicyChanged(uuid(7), true, Instant.EPOCH, 0L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user event outbox payload serialization failed");
        verifyNoInteractions(store);
    }

    private String policyEventId(UUID userId, long version) {
        return "ue:p:" + userId.toString().replace("-", "") + ":" + version;
    }
}
