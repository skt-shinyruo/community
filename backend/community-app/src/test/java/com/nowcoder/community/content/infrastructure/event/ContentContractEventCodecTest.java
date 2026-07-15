package com.nowcoder.community.content.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentContractEventCodec;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ContentTypedEvent;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
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

class ContentContractEventCodecTest {

    private static final JsonCodec JSON_CODEC = new JacksonJsonCodec(JsonMappers.standard());
    private static final UUID AGGREGATE_ID = uuid(900);
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-15T01:02:03Z");

    private final ContentContractEventCodec codec = new JacksonContentContractEventCodec(JSON_CODEC);

    @ParameterizedTest(name = "{0} -> {2}")
    @MethodSource("knownContentEvents")
    void everyKnownTypeShouldDecodeToItsSealedVariantAndRoundTrip(
            String type,
            JsonNode payload,
            Class<? extends ContentTypedEvent> expectedVariant,
            Class<?> expectedPayloadType
    ) throws ReflectiveOperationException {
        ContentContractEvent envelope = envelope(type, payload);

        ContentTypedEvent decoded = codec.decode(envelope);

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
    @MethodSource("knownContentTypes")
    void everyKnownTypeShouldRejectMalformedPayloadShape(String type) {
        assertThatThrownBy(() -> codec.decode(envelope(type, TextNode.valueOf("not-an-object"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(type);
    }

    @ParameterizedTest(name = "{0} validates {1}")
    @MethodSource("malformedContentIdentities")
    void decoderShouldRejectMalformedCoreIdentity(String type, String field, JsonNode payload) {
        assertThatThrownBy(() -> codec.decode(envelope(type, payload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(type)
                .hasMessageContaining(field);
    }

    @Test
    void unknownTypeShouldRetainRawJsonWithoutGuessingAPayloadClass() {
        JsonNode rawPayload = JSON_CODEC.valueToTree(Map.of("futureField", "kept", "revision", 2));
        ContentContractEvent envelope = envelope("FutureContentEvent", rawPayload);

        ContentTypedEvent decoded = codec.decode(envelope);

        assertThat(decoded).isInstanceOf(ContentTypedEvent.Unknown.class);
        ContentTypedEvent.Unknown unknown = (ContentTypedEvent.Unknown) decoded;
        assertThat(unknown.type()).isEqualTo("FutureContentEvent");
        assertThat(unknown.payload()).isEqualTo(rawPayload);
        assertThat(codec.encode(unknown)).isEqualTo(envelope);
    }

    @Test
    void unknownVariantMustNotBypassKnownTypePayloadBinding() {
        ContentTypedEvent.Unknown disguisedKnownType = new ContentTypedEvent.Unknown(
                "content:test:disguised",
                AGGREGATE_ID,
                "test",
                ContentEventTypes.POST_PUBLISHED,
                OCCURRED_AT,
                7L,
                JSON_CODEC.valueToTree(Map.of("actorUserId", uuid(777)))
        );

        assertThatThrownBy(() -> codec.encode(disguisedKnownType))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ContentEventTypes.POST_PUBLISHED);
    }

    @Test
    void typedEventFamilyShouldBeClosedAndBindEachKnownVariantToOnePayloadType() {
        assertThat(ContentTypedEvent.class.isSealed()).isTrue();
        assertThat(ContentTypedEvent.class.getPermittedSubclasses()).containsExactlyInAnyOrder(
                ContentTypedEvent.PostPublished.class,
                ContentTypedEvent.PostUpdated.class,
                ContentTypedEvent.PostDeleted.class,
                ContentTypedEvent.CommentCreated.class,
                ContentTypedEvent.CommentDeleted.class,
                ContentTypedEvent.ModerationActionApplied.class,
                ContentTypedEvent.Unknown.class
        );
        assertThat(Arrays.stream(ContentContractEventCodec.class.getMethods())
                .filter(method -> method.getName().equals("encode"))
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .toList())
                .containsExactly(ContentTypedEvent.class)
                .doesNotContain(Object.class, String.class);
    }

    private static Stream<Arguments> knownContentEvents() {
        JsonNode post = JSON_CODEC.valueToTree(postPayload());
        JsonNode comment = JSON_CODEC.valueToTree(commentPayload());
        JsonNode moderation = JSON_CODEC.valueToTree(moderationPayload());
        return Stream.of(
                Arguments.of(ContentEventTypes.POST_PUBLISHED, post,
                        ContentTypedEvent.PostPublished.class, PostPayload.class),
                Arguments.of(ContentEventTypes.POST_UPDATED, post,
                        ContentTypedEvent.PostUpdated.class, PostPayload.class),
                Arguments.of(ContentEventTypes.POST_DELETED, post,
                        ContentTypedEvent.PostDeleted.class, PostPayload.class),
                Arguments.of(ContentEventTypes.COMMENT_CREATED, comment,
                        ContentTypedEvent.CommentCreated.class, CommentPayload.class),
                Arguments.of(ContentEventTypes.COMMENT_DELETED, comment,
                        ContentTypedEvent.CommentDeleted.class, CommentPayload.class),
                Arguments.of(ContentEventTypes.MODERATION_ACTION_APPLIED, moderation,
                        ContentTypedEvent.ModerationActionApplied.class, ModerationPayload.class)
        );
    }

    private static Stream<String> knownContentTypes() {
        return Stream.of(
                ContentEventTypes.POST_PUBLISHED,
                ContentEventTypes.POST_UPDATED,
                ContentEventTypes.POST_DELETED,
                ContentEventTypes.COMMENT_CREATED,
                ContentEventTypes.COMMENT_DELETED,
                ContentEventTypes.MODERATION_ACTION_APPLIED
        );
    }

    private static Stream<Arguments> knownTypesWithAbsentPayload() {
        return knownContentTypes().flatMap(type -> Stream.of(
                Arguments.of(type, null),
                Arguments.of(type, NullNode.instance)
        ));
    }

    private static Stream<Arguments> malformedContentIdentities() {
        return Stream.of(
                Arguments.of(ContentEventTypes.POST_PUBLISHED, "postId",
                        JSON_CODEC.valueToTree(Map.of("postId", "not-a-uuid"))),
                Arguments.of(ContentEventTypes.COMMENT_CREATED, "commentId",
                        JSON_CODEC.valueToTree(Map.of("commentId", "not-a-uuid"))),
                Arguments.of(ContentEventTypes.MODERATION_ACTION_APPLIED, "toUserId",
                        JSON_CODEC.valueToTree(Map.of("toUserId", "not-a-uuid")))
        );
    }

    private static ContentContractEvent envelope(String type, JsonNode payload) {
        return new ContentContractEvent(
                "content:test:" + type,
                AGGREGATE_ID,
                "test",
                type,
                OCCURRED_AT,
                7L,
                payload
        );
    }

    private static PostPayload postPayload() {
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(101));
        payload.setUserId(uuid(102));
        payload.setCreateTime(OCCURRED_AT);
        payload.setUpdateTime(OCCURRED_AT.plusSeconds(1));
        return payload;
    }

    private static CommentPayload commentPayload() {
        CommentPayload payload = new CommentPayload();
        payload.setCommentId(uuid(201));
        payload.setPostId(uuid(101));
        payload.setUserId(uuid(102));
        payload.setCreateTime(OCCURRED_AT);
        return payload;
    }

    private static ModerationPayload moderationPayload() {
        ModerationPayload payload = new ModerationPayload();
        payload.setReportId(uuid(301));
        payload.setToUserId(uuid(302));
        payload.setCreateTime(OCCURRED_AT);
        return payload;
    }
}
