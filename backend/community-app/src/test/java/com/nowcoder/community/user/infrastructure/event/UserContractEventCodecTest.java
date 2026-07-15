package com.nowcoder.community.user.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.user.contracts.event.UserContractEvent;
import com.nowcoder.community.user.contracts.event.UserContractEventCodec;
import com.nowcoder.community.user.contracts.event.UserEventTypes;
import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;
import com.nowcoder.community.user.contracts.event.UserTypedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserContractEventCodecTest {

    private static final JsonCodec JSON_CODEC = new JacksonJsonCodec(JsonMappers.standard());

    private final UserContractEventCodec codec = new JacksonUserContractEventCodec(JSON_CODEC);

    @ParameterizedTest(name = "{0} -> {2}")
    @MethodSource("knownUserEvents")
    void everyKnownTypeShouldDecodeToItsSealedVariantAndRoundTrip(
            String type,
            JsonNode payload,
            Class<? extends UserTypedEvent> expectedVariant,
            Class<?> expectedPayloadType
    ) throws ReflectiveOperationException {
        UserContractEvent envelope = envelope(type, payload);

        UserTypedEvent decoded = codec.decode(envelope);

        assertThat(decoded).isInstanceOf(expectedVariant);
        RecordComponent payloadComponent = Arrays.stream(expectedVariant.getRecordComponents())
                .filter(component -> component.getName().equals("payload"))
                .findFirst()
                .orElseThrow();
        assertThat(payloadComponent.getType()).isEqualTo(expectedPayloadType);
        assertThat(Arrays.stream(expectedVariant.getRecordComponents()).map(RecordComponent::getName).toList())
                .doesNotContain("type");
        assertThat(JSON_CODEC.valueToTree(payloadComponent.getAccessor().invoke(decoded))).isEqualTo(payload);
        assertThat(codec.encode(decoded)).isEqualTo(envelope);
    }

    @ParameterizedTest(name = "UserPolicyChanged rejects {0}")
    @MethodSource("absentUserPayloads")
    void knownTypeShouldRejectMissingOrNullPayload(String description, JsonNode payload) {
        assertThatThrownBy(() -> codec.decode(envelope(UserEventTypes.USER_POLICY_CHANGED, payload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(UserEventTypes.USER_POLICY_CHANGED);
    }

    @Test
    void knownTypeShouldRejectMalformedPayloadShape() {
        assertThatThrownBy(() -> codec.decode(envelope(
                UserEventTypes.USER_POLICY_CHANGED,
                TextNode.valueOf("not-an-object")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(UserEventTypes.USER_POLICY_CHANGED);
    }

    @Test
    void decoderShouldRejectMalformedCoreIdentity() {
        JsonNode payload = JSON_CODEC.valueToTree(Map.of(
                "userId", "not-a-uuid",
                "occurredAtEpochMillis", 1_773_800_000_000L,
                "version", 13L
        ));

        assertThatThrownBy(() -> codec.decode(envelope(UserEventTypes.USER_POLICY_CHANGED, payload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(UserEventTypes.USER_POLICY_CHANGED)
                .hasMessageContaining("userId");
    }

    @Test
    void unknownTypeShouldRetainRawJsonWithoutGuessingAPayloadClass() {
        JsonNode rawPayload = JSON_CODEC.valueToTree(Map.of("futureField", "kept", "revision", 2));
        UserContractEvent envelope = envelope("FutureUserEvent", rawPayload);

        UserTypedEvent decoded = codec.decode(envelope);

        assertThat(decoded).isInstanceOf(UserTypedEvent.Unknown.class);
        UserTypedEvent.Unknown unknown = (UserTypedEvent.Unknown) decoded;
        assertThat(unknown.type()).isEqualTo("FutureUserEvent");
        assertThat(unknown.payload()).isEqualTo(rawPayload);
        assertThat(codec.encode(unknown)).isEqualTo(envelope);
    }

    @Test
    void unknownVariantMustNotBypassKnownTypePayloadBinding() {
        UserTypedEvent.Unknown disguisedKnownType = new UserTypedEvent.Unknown(
                "user:test:disguised",
                UserEventTypes.USER_POLICY_CHANGED,
                JSON_CODEC.valueToTree(Map.of("actorUserId", uuid(777)))
        );

        assertThatThrownBy(() -> codec.encode(disguisedKnownType))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(UserEventTypes.USER_POLICY_CHANGED);
    }

    @Test
    void typedEventFamilyShouldBeClosedAndBindTheKnownVariantToItsPayloadType() {
        assertThat(UserTypedEvent.class.isSealed()).isTrue();
        assertThat(UserTypedEvent.class.getPermittedSubclasses()).containsExactlyInAnyOrder(
                UserTypedEvent.UserPolicyChanged.class,
                UserTypedEvent.Unknown.class
        );
        assertThat(Arrays.stream(UserContractEventCodec.class.getMethods())
                .filter(method -> method.getName().equals("encode"))
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .toList())
                .containsExactly(UserTypedEvent.class)
                .doesNotContain(Object.class, String.class);
    }

    private static Stream<Arguments> knownUserEvents() {
        return Stream.of(Arguments.of(
                UserEventTypes.USER_POLICY_CHANGED,
                JSON_CODEC.valueToTree(policyPayload()),
                UserTypedEvent.UserPolicyChanged.class,
                UserPolicyChangedPayload.class
        ));
    }

    private static Stream<Arguments> absentUserPayloads() {
        return Stream.of(
                Arguments.of("missing payload", null),
                Arguments.of("JSON null", NullNode.instance)
        );
    }

    private static UserContractEvent envelope(String type, JsonNode payload) {
        return new UserContractEvent("user:test:" + type, type, payload);
    }

    private static UserPolicyChangedPayload policyPayload() {
        UserPolicyChangedPayload payload = new UserPolicyChangedPayload();
        payload.setUserId(uuid(301));
        payload.setUserExists(true);
        payload.setSuspended(false);
        payload.setMuted(true);
        payload.setMuteUntil(1_784_078_400_000L);
        payload.setBanUntil(null);
        payload.setCanSendPrivate(false);
        payload.setOccurredAtEpochMillis(1_773_800_000_000L);
        payload.setVersion(13L);
        return payload;
    }
}
