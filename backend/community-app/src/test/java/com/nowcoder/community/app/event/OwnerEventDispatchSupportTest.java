package com.nowcoder.community.app.event;

import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.json.event.OwnerEventDispatchSupport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnerEventDispatchSupportTest {

    @Test
    void basicDispatchShouldDecodeValidateAndDispatchTheOwnerEnvelope() {
        TestEvent event = new TestEvent("event-1", "UserPolicyChanged");
        AtomicReference<String> decodedJson = new AtomicReference<>();
        AtomicReference<TestEvent> validatedEvent = new AtomicReference<>();
        AtomicReference<String> dispatchedKey = new AtomicReference<>();
        AtomicReference<TestEvent> dispatchedEvent = new AtomicReference<>();
        OwnerEventDispatchSupport<TestEvent> support = OwnerEventDispatchSupport.basic(
                "user",
                json -> {
                    decodedJson.set(json);
                    return event;
                },
                validatedEvent::set,
                value -> new OwnerEventDispatchSupport.EnvelopeMetadata(value.eventId(), value.type()),
                (key, value) -> {
                    dispatchedKey.set(key);
                    dispatchedEvent.set(value);
                }
        );

        support.dispatch("user-1", "{\"eventId\":\"event-1\"}");

        assertThat(decodedJson).hasValue("{\"eventId\":\"event-1\"}");
        assertThat(validatedEvent).hasValue(event);
        assertThat(dispatchedKey).hasValue("user-1");
        assertThat(dispatchedEvent).hasValue(event);
    }

    @Test
    void versionedDispatchShouldRejectMissingMetadataInWireOrder() {
        UUID aggregateId = UUID.randomUUID();
        OwnerEventDispatchSupport<TestEvent> support = OwnerEventDispatchSupport.versioned(
                "content",
                ignored -> new TestEvent("event-1", "PostPublished"),
                ignored -> {
                },
                ignored -> new OwnerEventDispatchSupport.VersionedEnvelopeMetadata(
                        "event-1", "PostPublished", aggregateId, " ", Instant.EPOCH, 1L),
                (key, event) -> {
                    throw new AssertionError("invalid envelope must not be dispatched");
                }
        );

        assertThatThrownBy(() -> support.dispatch("post-1", "{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("content event outbox payload missing aggregateType");
    }

    @Test
    void dispatchShouldPreserveCodecAndTypedPayloadFailureSemantics() {
        JsonCodecException codecFailure = new JsonCodecException("bad json", new RuntimeException("boom"));
        OwnerEventDispatchSupport<TestEvent> parseFailure = OwnerEventDispatchSupport.basic(
                "social",
                ignored -> {
                    throw codecFailure;
                },
                ignored -> {
                },
                event -> new OwnerEventDispatchSupport.EnvelopeMetadata(event.eventId(), event.type()),
                (key, event) -> {
                }
        );
        IllegalArgumentException payloadFailure = new IllegalArgumentException("invalid social event payload");
        OwnerEventDispatchSupport<TestEvent> validationFailure = OwnerEventDispatchSupport.basic(
                "social",
                ignored -> new TestEvent("event-1", "LikeCreated"),
                ignored -> {
                    throw payloadFailure;
                },
                event -> new OwnerEventDispatchSupport.EnvelopeMetadata(event.eventId(), event.type()),
                (key, event) -> {
                }
        );

        assertThatThrownBy(() -> parseFailure.dispatch("key", "{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("social event outbox payload deserialization failed")
                .hasCause(codecFailure);
        assertThatThrownBy(() -> validationFailure.dispatch("key", "{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("invalid social event payload")
                .hasCause(payloadFailure);
    }

    @Test
    void dispatchShouldRejectBlankPayloadBeforeCallingTheCodec() {
        OwnerEventDispatchSupport<TestEvent> support = OwnerEventDispatchSupport.basic(
                "user",
                ignored -> {
                    throw new AssertionError("blank JSON must not reach the codec");
                },
                ignored -> {
                },
                event -> new OwnerEventDispatchSupport.EnvelopeMetadata(event.eventId(), event.type()),
                (key, event) -> {
                }
        );

        assertThatThrownBy(() -> support.dispatch("key", " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("user event outbox payload is blank");
    }

    private record TestEvent(String eventId, String type) {
    }
}
