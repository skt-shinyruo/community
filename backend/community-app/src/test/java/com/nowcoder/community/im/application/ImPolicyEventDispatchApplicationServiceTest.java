package com.nowcoder.community.im.application;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.im.application.command.DispatchImPolicyEventCommand;
import com.nowcoder.community.im.common.event.UserBlockRelationChanged;
import com.nowcoder.community.im.common.event.UserMessagingPolicyChanged;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ImPolicyEventDispatchApplicationServiceTest {

    private final ImPolicyIntegrationEventDispatcher dispatcher = mock(ImPolicyIntegrationEventDispatcher.class);
    private final ImPolicyEventDispatchApplicationService service =
            new ImPolicyEventDispatchApplicationService(
                    new JacksonJsonCodec(JsonMappers.standard()),
                    dispatcher
            );

    @Test
    void dispatchShouldPublishCurrentPolicyStateThroughPort() {
        Instant muteUntil = Instant.parse("2026-04-24T09:15:30Z");
        Instant expiredBanUntil = Instant.parse("2026-04-22T09:15:30Z");

        service.dispatch(new DispatchImPolicyEventCommand(
                "evt-policy-1",
                "key",
                "{\"kind\":\"USER_POLICY\",\"primaryUserId\":\"" + uuid(7)
                        + "\",\"userExists\":true,\"suspended\":false,\"muted\":true,\"muteUntil\":" + muteUntil.toEpochMilli()
                        + ",\"banUntil\":" + expiredBanUntil.toEpochMilli()
                        + ",\"canSendPrivate\":false,\"occurredAtEpochMillis\":1712345678901,\"version\":7007}"
        ));

        ArgumentCaptor<UserMessagingPolicyChanged> eventCaptor = ArgumentCaptor.forClass(UserMessagingPolicyChanged.class);
        verify(dispatcher).dispatchUserMessagingPolicyChanged(org.mockito.ArgumentMatchers.eq(uuid(7).toString()), eventCaptor.capture());
        UserMessagingPolicyChanged event = eventCaptor.getValue();
        assertThat(event.eventId()).isEqualTo("evt-policy-1");
        assertThat(event.userId()).isEqualTo(uuid(7));
        assertThat(event.userExists()).isTrue();
        assertThat(event.muted()).isTrue();
        assertThat(event.suspended()).isFalse();
        assertThat(event.muteUntil()).isEqualTo(muteUntil.toEpochMilli());
        assertThat(event.banUntil()).isEqualTo(expiredBanUntil.toEpochMilli());
        assertThat(event.version()).isEqualTo(7007L);
    }

    @Test
    void dispatchShouldIgnoreLegacyModerationKind() {
        service.dispatch(new DispatchImPolicyEventCommand(
                "evt-policy-2",
                "key",
                "{\"kind\":\"MODERATION\",\"userId\":\"" + uuid(7)
                        + "\",\"userExists\":true,\"occurredAtEpochMillis\":1712345678901}"
        ));

        verifyNoInteractions(dispatcher);
    }

    @Test
    void dispatchShouldPublishCurrentBlockStateThroughPort() {
        service.dispatch(new DispatchImPolicyEventCommand(
                "evt-policy-3",
                "key",
                "{\"kind\":\"BLOCK\",\"primaryUserId\":\"" + uuid(7)
                        + "\",\"secondaryUserId\":\"" + uuid(8)
                        + "\",\"active\":true,\"occurredAtEpochMillis\":1712345678902,\"version\":8008}"
        ));

        ArgumentCaptor<UserBlockRelationChanged> eventCaptor = ArgumentCaptor.forClass(UserBlockRelationChanged.class);
        verify(dispatcher).dispatchUserBlockRelationChanged(org.mockito.ArgumentMatchers.eq(uuid(7).toString()), eventCaptor.capture());
        UserBlockRelationChanged event = eventCaptor.getValue();
        assertThat(event.eventId()).isEqualTo("evt-policy-3");
        assertThat(event.blockerUserId()).isEqualTo(uuid(7));
        assertThat(event.blockedUserId()).isEqualTo(uuid(8));
        assertThat(event.active()).isTrue();
        assertThat(event.version()).isEqualTo(8008L);
    }

    @Test
    void dispatchShouldIgnoreLegacyFallbackFieldNames() {
        service.dispatch(new DispatchImPolicyEventCommand(
                "evt-policy-4",
                "key",
                "{\"kind\":\"BLOCK\",\"blockerUserId\":\"" + uuid(7)
                        + "\",\"blockedUserId\":\"" + uuid(8)
                        + "\",\"active\":true,\"occurredAtEpochMillis\":1712345678902}"
        ));

        verifyNoInteractions(dispatcher);
    }

    @Test
    void dispatchShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.dispatch(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void dispatchShouldIgnoreBlankPayloadUnknownKindAndMissingInvalidIds() {
        service.dispatch(new DispatchImPolicyEventCommand("evt-blank", "key", " "));
        service.dispatch(new DispatchImPolicyEventCommand("evt-unknown", "key", "{\"kind\":\"OTHER\"}"));
        service.dispatch(new DispatchImPolicyEventCommand("evt-missing-user", "key", "{\"kind\":\"USER_POLICY\",\"occurredAtEpochMillis\":1}"));
        service.dispatch(new DispatchImPolicyEventCommand("evt-invalid-block", "key", "{\"kind\":\"BLOCK\",\"primaryUserId\":\"not-uuid\",\"occurredAtEpochMillis\":1}"));

        verifyNoInteractions(dispatcher);
    }

    @Test
    void dispatchShouldFailMalformedJsonForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatch(new DispatchImPolicyEventCommand("evt", "key", "{not-json")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("im policy outbox payload");
    }

    @Test
    void dispatchShouldFailMissingOccurredAtForRecognizedDispatchablePayload() {
        assertThatThrownBy(() -> service.dispatch(new DispatchImPolicyEventCommand(
                "evt",
                "key",
                "{\"kind\":\"USER_POLICY\",\"primaryUserId\":\"" + uuid(7) + "\"}"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("occurredAtEpochMillis");
    }

    @Test
    void dispatchShouldPropagatePortFailureForOutboxRetry() {
        RuntimeException failure = new RuntimeException("kafka down");
        doThrow(failure).when(dispatcher).dispatchUserMessagingPolicyChanged(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> service.dispatch(new DispatchImPolicyEventCommand(
                "evt",
                "key",
                "{\"kind\":\"USER_POLICY\",\"primaryUserId\":\"" + uuid(7)
                        + "\",\"occurredAtEpochMillis\":1712345678901}"
        )))
                .isSameAs(failure);
    }
}
