package com.nowcoder.community.social.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialContractEventCodec;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.social.contracts.event.SocialTypedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocialContractEventCodecTest {

    private static final JsonCodec JSON_CODEC = new JacksonJsonCodec(JsonMappers.standard());
    private static final UUID AGGREGATE_ID = uuid(900);
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-15T02:03:04Z");

    private final SocialContractEventCodec codec = new JacksonSocialContractEventCodec(JSON_CODEC);

    @ParameterizedTest(name = "{0} -> {2}")
    @MethodSource("knownSocialEvents")
    void everyKnownTypeShouldDecodeToItsSealedVariantAndRoundTrip(
            String type,
            JsonNode payload,
            Class<? extends SocialTypedEvent> expectedVariant,
            Class<?> expectedPayloadType
    ) throws ReflectiveOperationException {
        SocialContractEvent envelope = envelope(type, payload);

        SocialTypedEvent decoded = codec.decode(envelope);

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

    @ParameterizedTest(name = "{0} rejects absent payload")
    @MethodSource("knownTypesWithAbsentPayload")
    void everyKnownTypeShouldRejectMissingOrNullPayload(String type, JsonNode payload) {
        assertThatThrownBy(() -> codec.decode(envelope(type, payload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(type);
    }

    @ParameterizedTest(name = "{0} rejects non-object payload")
    @MethodSource("knownSocialTypes")
    void everyKnownTypeShouldRejectMalformedPayloadShape(String type) {
        assertThatThrownBy(() -> codec.decode(envelope(type, TextNode.valueOf("not-an-object"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(type);
    }

    @ParameterizedTest(name = "{0} validates {1}")
    @MethodSource("malformedSocialIdentities")
    void decoderShouldRejectMalformedCoreIdentity(String type, String field, JsonNode payload) {
        assertThatThrownBy(() -> codec.decode(envelope(type, payload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(type)
                .hasMessageContaining(field);
    }

    @Test
    void unknownTypeShouldRetainRawJsonWithoutGuessingAPayloadClass() {
        JsonNode rawPayload = JSON_CODEC.valueToTree(Map.of("futureField", "kept", "revision", 2));
        SocialContractEvent envelope = envelope("FutureSocialEvent", rawPayload);

        SocialTypedEvent decoded = codec.decode(envelope);

        assertThat(decoded).isInstanceOf(SocialTypedEvent.Unknown.class);
        SocialTypedEvent.Unknown unknown = (SocialTypedEvent.Unknown) decoded;
        assertThat(unknown.type()).isEqualTo("FutureSocialEvent");
        assertThat(unknown.payload()).isEqualTo(rawPayload);
        assertThat(codec.encode(unknown)).isEqualTo(envelope);
    }

    @Test
    void unknownVariantMustNotBypassKnownTypePayloadBinding() {
        SocialTypedEvent.Unknown disguisedKnownType = new SocialTypedEvent.Unknown(
                "social:test:disguised",
                AGGREGATE_ID,
                "test",
                SocialEventTypes.LIKE_CREATED,
                OCCURRED_AT,
                11L,
                JSON_CODEC.valueToTree(Map.of("postId", uuid(777)))
        );

        assertThatThrownBy(() -> codec.encode(disguisedKnownType))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SocialEventTypes.LIKE_CREATED);
    }

    @Test
    void likePayloadShouldRoundTripLifecycleIdentityAndDecodeLegacyPayloadWithoutIt() {
        UUID relationInstanceId = uuid(104);
        LikePayload currentPayload = likePayload();
        currentPayload.setRelationInstanceId(relationInstanceId);

        SocialTypedEvent current = codec.decode(envelope(
                SocialEventTypes.LIKE_CREATED,
                JSON_CODEC.valueToTree(currentPayload)
        ));

        assertThat(((SocialTypedEvent.LikeCreated) current).payload().getRelationInstanceId())
                .isEqualTo(relationInstanceId);
        assertThat(codec.encode(current).payload().path("relationInstanceId").asText())
                .isEqualTo(relationInstanceId.toString());

        JsonNode legacyPayload = JSON_CODEC.valueToTree(Map.of(
                "actorUserId", uuid(101),
                "entityType", EntityTypes.POST,
                "entityId", uuid(102),
                "entityUserId", uuid(103),
                "postId", uuid(102),
                "relationKey", "post:102:user:101"
        ));
        SocialTypedEvent legacy = codec.decode(envelope(SocialEventTypes.LIKE_REMOVED, legacyPayload));

        assertThat(((SocialTypedEvent.LikeRemoved) legacy).payload().getRelationInstanceId()).isNull();
    }

    @Test
    void typedEventFamilyShouldBeClosedAndBindEachKnownVariantToOnePayloadType() {
        assertThat(SocialTypedEvent.class.isSealed()).isTrue();
        assertThat(SocialTypedEvent.class.getPermittedSubclasses()).containsExactlyInAnyOrder(
                SocialTypedEvent.LikeCreated.class,
                SocialTypedEvent.LikeRemoved.class,
                SocialTypedEvent.FollowCreated.class,
                SocialTypedEvent.BlockRelationChanged.class,
                SocialTypedEvent.Unknown.class
        );
        assertThat(Arrays.stream(SocialContractEventCodec.class.getMethods())
                .filter(method -> method.getName().equals("encode"))
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .toList())
                .containsExactly(SocialTypedEvent.class)
                .doesNotContain(Object.class, String.class);
    }

    private static Stream<Arguments> knownSocialEvents() {
        JsonNode like = JSON_CODEC.valueToTree(likePayload());
        JsonNode follow = JSON_CODEC.valueToTree(followPayload());
        JsonNode block = JSON_CODEC.valueToTree(blockPayload());
        return Stream.of(
                Arguments.of(SocialEventTypes.LIKE_CREATED, like,
                        SocialTypedEvent.LikeCreated.class, LikePayload.class),
                Arguments.of(SocialEventTypes.LIKE_REMOVED, like,
                        SocialTypedEvent.LikeRemoved.class, LikePayload.class),
                Arguments.of(SocialEventTypes.FOLLOW_CREATED, follow,
                        SocialTypedEvent.FollowCreated.class, FollowPayload.class),
                Arguments.of(SocialEventTypes.BLOCK_RELATION_CHANGED, block,
                        SocialTypedEvent.BlockRelationChanged.class, BlockPayload.class)
        );
    }

    private static Stream<String> knownSocialTypes() {
        return Stream.of(
                SocialEventTypes.LIKE_CREATED,
                SocialEventTypes.LIKE_REMOVED,
                SocialEventTypes.FOLLOW_CREATED,
                SocialEventTypes.BLOCK_RELATION_CHANGED
        );
    }

    private static Stream<Arguments> knownTypesWithAbsentPayload() {
        return knownSocialTypes().flatMap(type -> Stream.of(
                Arguments.of(type, null),
                Arguments.of(type, NullNode.instance)
        ));
    }

    private static Stream<Arguments> malformedSocialIdentities() {
        return Stream.of(
                Arguments.of(SocialEventTypes.LIKE_CREATED, "actorUserId",
                        JSON_CODEC.valueToTree(Map.of("actorUserId", "not-a-uuid"))),
                Arguments.of(SocialEventTypes.FOLLOW_CREATED, "entityId",
                        JSON_CODEC.valueToTree(Map.of("entityId", "not-a-uuid"))),
                Arguments.of(SocialEventTypes.BLOCK_RELATION_CHANGED, "blocked",
                        JSON_CODEC.valueToTree(Map.of(
                                "blockerUserId", uuid(1),
                                "blockedUserId", uuid(2),
                                "blocked", "not-a-boolean")))
        );
    }

    private static SocialContractEvent envelope(String type, JsonNode payload) {
        return new SocialContractEvent(
                "social:test:" + type,
                AGGREGATE_ID,
                "test",
                type,
                OCCURRED_AT,
                11L,
                payload
        );
    }

    private static LikePayload likePayload() {
        LikePayload payload = new LikePayload();
        payload.setActorUserId(uuid(101));
        payload.setEntityType(EntityTypes.POST);
        payload.setEntityId(uuid(102));
        payload.setEntityUserId(uuid(103));
        payload.setPostId(uuid(102));
        payload.setRelationKey("post:102:user:101");
        return payload;
    }

    private static FollowPayload followPayload() {
        FollowPayload payload = new FollowPayload();
        payload.setActorUserId(uuid(201));
        payload.setEntityType(EntityTypes.USER);
        payload.setEntityId(uuid(202));
        payload.setEntityUserId(uuid(202));
        payload.setCreateTime(OCCURRED_AT);
        return payload;
    }

    private static BlockPayload blockPayload() {
        BlockPayload payload = new BlockPayload();
        payload.setBlockerUserId(uuid(301));
        payload.setBlockedUserId(uuid(302));
        payload.setBlocked(true);
        payload.setOccurredAt(OCCURRED_AT);
        payload.setVersion(11L);
        return payload;
    }
}
