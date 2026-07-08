package com.nowcoder.community.content.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.content.application.ContentEventDispatchApplicationService;
import com.nowcoder.community.content.application.ContentIntegrationEventDispatcher;
import com.nowcoder.community.content.application.command.DispatchContentEventCommand;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class OutboxContentEventPublisherTest {

    private static final String TOPIC = "custom.eventbus.content";

    @Test
    void publisherShouldOnlyDefaultWhenOutboxWorkerIsEnabled() {
        ConditionalOnExpression conditional = OutboxContentEventPublisher.class.getAnnotation(ConditionalOnExpression.class);

        assertThat(conditional).isNotNull();
        assertThat(conditional.value()).isEqualTo(
                "'${content.events.publisher:outbox-kafka}' == 'outbox-kafka' && '${events.outbox.enabled:true}' == 'true'"
        );
    }

    @Test
    void postPublishedShouldWriteContentContractEnvelopeToOutbox() throws Exception {
        ObjectMapper objectMapper = JsonMappers.standard();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        UUID postId = uuid(101);
        OutboxContentEventPublisher publisher = new OutboxContentEventPublisher(
                new JacksonJsonCodec(JsonMappers.standard()),
                store,
                TOPIC
        );

        PostPayload payload = new PostPayload();
        payload.setPostId(postId);
        payload.setCreateTime(Instant.EPOCH);

        publisher.publishPostPublished(payload);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(
                eq("content:PostPublished:" + postId),
                eq(TOPIC),
                eq(postId.toString()),
                payloadCaptor.capture()
        );
        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(json.path("eventId").asText()).isEqualTo("content:PostPublished:" + postId);
        assertThat(json.path("type").asText()).isEqualTo(ContentEventTypes.POST_PUBLISHED);
        assertThat(json.path("payload").path("postId").asText()).isEqualTo(postId.toString());
    }

    @Test
    void publishPostPublishedShouldSerializeBackboneMetadata() {
        JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        OutboxContentEventPublisher publisher = new OutboxContentEventPublisher(jsonCodec, store, "eventbus.content");
        UUID postId = UUID.randomUUID();
        PostPayload payload = new PostPayload();
        payload.setPostId(postId);
        payload.setCreateTime(Instant.parse("2026-07-06T08:00:00Z"));

        publisher.publishPostPublished(payload);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(eq("content:PostPublished:" + postId), eq("eventbus.content"), eq(postId.toString()), payloadCaptor.capture());
        ContentContractEvent event = jsonCodec.fromJson(payloadCaptor.getValue(), ContentContractEvent.class);
        assertThat(event.aggregateId()).isEqualTo(postId);
        assertThat(event.aggregateType()).isEqualTo("post");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-07-06T08:00:00Z"));
        assertThat(event.version()).isPositive();
    }

    @Test
    void contentEventbusPayloadShouldCarryOwnerFactFieldsForP2Projections() throws Exception {
        ObjectMapper objectMapper = JsonMappers.standard();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        OutboxContentEventPublisher publisher = new OutboxContentEventPublisher(
                new JacksonJsonCodec(JsonMappers.standard()),
                store,
                "eventbus.content"
        );
        UUID postId = uuid(701);
        UUID postAuthorId = uuid(7);
        UUID categoryId = uuid(3);
        UUID commentId = uuid(702);
        UUID commenterId = uuid(8);
        UUID targetUserId = uuid(9);
        PostPayload postPayload = postPayload(postId);
        postPayload.setUserId(postAuthorId);
        postPayload.setCategoryId(categoryId);
        postPayload.setTags(List.of("java", "reliability"));
        postPayload.setTitle("P2 post");
        postPayload.setContent("source content");
        postPayload.setStatus(0);
        postPayload.setScore(1.5);
        CommentPayload commentPayload = commentPayload(commentId);
        commentPayload.setPostId(postId);
        commentPayload.setUserId(commenterId);
        commentPayload.setEntityType(1);
        commentPayload.setEntityId(postId);
        commentPayload.setTargetUserId(targetUserId);
        commentPayload.setContent("comment body");

        publisher.publishPostPublished(postPayload);
        publisher.publishCommentCreated(commentPayload);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store, times(2)).enqueue(org.mockito.ArgumentMatchers.anyString(), eq("eventbus.content"), org.mockito.ArgumentMatchers.anyString(), payloadCaptor.capture());
        JsonNode postEvent = objectMapper.readTree(payloadCaptor.getAllValues().get(0));
        JsonNode commentEvent = objectMapper.readTree(payloadCaptor.getAllValues().get(1));
        assertThat(postEvent.path("eventId").asText()).isEqualTo("content:PostPublished:" + postId);
        assertThat(postEvent.path("aggregateId").asText()).isEqualTo(postId.toString());
        assertThat(postEvent.path("aggregateType").asText()).isEqualTo("post");
        assertThat(postEvent.path("type").asText()).isEqualTo(ContentEventTypes.POST_PUBLISHED);
        assertThat(postEvent.path("version").asLong()).isPositive();
        assertThat(postEvent.path("payload").path("postId").asText()).isEqualTo(postId.toString());
        assertThat(postEvent.path("payload").path("userId").asText()).isEqualTo(postAuthorId.toString());
        assertThat(postEvent.path("payload").path("categoryId").asText()).isEqualTo(categoryId.toString());
        assertThat(postEvent.path("payload").path("tags")).hasSize(2);
        assertThat(postEvent.path("payload").path("tags").get(0).asText()).isEqualTo("java");
        assertThat(postEvent.path("payload").path("tags").get(1).asText()).isEqualTo("reliability");
        assertThat(postEvent.path("payload").path("title").asText()).isEqualTo("P2 post");
        assertThat(postEvent.path("payload").path("content").asText()).isEqualTo("source content");
        assertThat(postEvent.path("payload").path("status").asInt()).isZero();
        assertThat(postEvent.path("payload").path("score").asDouble()).isEqualTo(1.5);
        assertThat(commentEvent.path("eventId").asText()).isEqualTo("content:CommentCreated:" + commentId);
        assertThat(commentEvent.path("aggregateId").asText()).isEqualTo(commentId.toString());
        assertThat(commentEvent.path("aggregateType").asText()).isEqualTo("comment");
        assertThat(commentEvent.path("type").asText()).isEqualTo(ContentEventTypes.COMMENT_CREATED);
        assertThat(commentEvent.path("version").asLong()).isPositive();
        assertThat(commentEvent.path("payload").path("commentId").asText()).isEqualTo(commentId.toString());
        assertThat(commentEvent.path("payload").path("postId").asText()).isEqualTo(postId.toString());
        assertThat(commentEvent.path("payload").path("userId").asText()).isEqualTo(commenterId.toString());
        assertThat(commentEvent.path("payload").path("targetUserId").asText()).isEqualTo(targetUserId.toString());
        assertThat(commentEvent.path("payload").path("content").asText()).isEqualTo("comment body");
    }

    @Test
    void publishPostPublishedShouldRejectMissingSourceTimestamp() {
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        UUID postId = uuid(612);
        OutboxContentEventPublisher publisher = new OutboxContentEventPublisher(
                new JacksonJsonCodec(JsonMappers.standard()),
                store,
                TOPIC
        );
        PostPayload payload = new PostPayload();
        payload.setPostId(postId);

        assertThatThrownBy(() -> publisher.publishPostPublished(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event source occurredAt missing");

        verifyNoInteractions(store);
    }

    @Test
    void publishedContentOutboxPayloadsShouldDispatchAsTypedKafkaContractEvents() {
        JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        ContentIntegrationEventDispatcher dispatcher = mock(ContentIntegrationEventDispatcher.class);
        UUID publishedPostId = uuid(606);
        UUID updatedPostId = uuid(607);
        UUID deletedPostId = uuid(608);
        UUID createdCommentId = uuid(609);
        UUID deletedCommentId = uuid(610);
        UUID moderatedUserId = uuid(611);
        OutboxContentEventPublisher publisher = new OutboxContentEventPublisher(jsonCodec, store, TOPIC);
        ContentEventDispatchApplicationService dispatchService =
                new ContentEventDispatchApplicationService(jsonCodec, dispatcher);

        publisher.publishPostPublished(postPayload(publishedPostId));
        publisher.publishPostUpdated(postPayload(updatedPostId));
        publisher.publishPostDeleted(postPayload(deletedPostId));
        publisher.publishCommentCreated(commentPayload(createdCommentId));
        publisher.publishCommentDeleted(commentPayload(deletedCommentId));
        publisher.publishModerationActionApplied(moderationPayload(moderatedUserId));

        ArgumentCaptor<String> eventIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store, times(6)).enqueue(
                eventIdCaptor.capture(),
                eq(TOPIC),
                keyCaptor.capture(),
                payloadCaptor.capture()
        );
        for (int i = 0; i < payloadCaptor.getAllValues().size(); i++) {
            dispatchService.dispatch(new DispatchContentEventCommand(
                    keyCaptor.getAllValues().get(i),
                    payloadCaptor.getAllValues().get(i)
            ));
        }

        ArgumentCaptor<String> dispatchedKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ContentContractEvent> eventCaptor = ArgumentCaptor.forClass(ContentContractEvent.class);
        verify(dispatcher, times(6)).dispatch(dispatchedKeyCaptor.capture(), eventCaptor.capture());
        assertThat(dispatchedKeyCaptor.getAllValues()).containsExactlyElementsOf(keyCaptor.getAllValues());
        assertThat(eventCaptor.getAllValues())
                .extracting(ContentContractEvent::eventId)
                .containsExactlyElementsOf(eventIdCaptor.getAllValues());
        assertThat(eventCaptor.getAllValues())
                .extracting(ContentContractEvent::type)
                .containsExactly(
                        ContentEventTypes.POST_PUBLISHED,
                        ContentEventTypes.POST_UPDATED,
                        ContentEventTypes.POST_DELETED,
                        ContentEventTypes.COMMENT_CREATED,
                        ContentEventTypes.COMMENT_DELETED,
                        ContentEventTypes.MODERATION_ACTION_APPLIED
                );
        assertThat(eventCaptor.getAllValues().get(0).payload()).isInstanceOf(PostPayload.class);
        assertThat(((PostPayload) eventCaptor.getAllValues().get(0).payload()).getPostId()).isEqualTo(publishedPostId);
        assertThat(eventCaptor.getAllValues().get(1).payload()).isInstanceOf(PostPayload.class);
        assertThat(((PostPayload) eventCaptor.getAllValues().get(1).payload()).getPostId()).isEqualTo(updatedPostId);
        assertThat(eventCaptor.getAllValues().get(2).payload()).isInstanceOf(PostPayload.class);
        assertThat(((PostPayload) eventCaptor.getAllValues().get(2).payload()).getPostId()).isEqualTo(deletedPostId);
        assertThat(eventCaptor.getAllValues().get(3).payload()).isInstanceOf(CommentPayload.class);
        assertThat(((CommentPayload) eventCaptor.getAllValues().get(3).payload()).getCommentId()).isEqualTo(createdCommentId);
        assertThat(eventCaptor.getAllValues().get(4).payload()).isInstanceOf(CommentPayload.class);
        assertThat(((CommentPayload) eventCaptor.getAllValues().get(4).payload()).getCommentId()).isEqualTo(deletedCommentId);
        assertThat(eventCaptor.getAllValues().get(5).payload()).isInstanceOf(ModerationPayload.class);
        assertThat(((ModerationPayload) eventCaptor.getAllValues().get(5).payload()).getToUserId()).isEqualTo(moderatedUserId);
    }

    @Test
    void commentCreatedShouldUseStableCommentEventIdAndKey() throws Exception {
        ObjectMapper objectMapper = JsonMappers.standard();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        UUID commentId = uuid(202);
        OutboxContentEventPublisher publisher = new OutboxContentEventPublisher(
                new JacksonJsonCodec(JsonMappers.standard()),
                store,
                TOPIC
        );

        CommentPayload payload = new CommentPayload();
        payload.setCommentId(commentId);
        payload.setCreateTime(Instant.EPOCH);

        publisher.publishCommentCreated(payload);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(
                eq("content:CommentCreated:" + commentId),
                eq(TOPIC),
                eq(commentId.toString()),
                payloadCaptor.capture()
        );
        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(json.path("eventId").asText()).isEqualTo("content:CommentCreated:" + commentId);
        assertThat(json.path("type").asText()).isEqualTo(ContentEventTypes.COMMENT_CREATED);
        assertThat(json.path("payload").path("commentId").asText()).isEqualTo(commentId.toString());
    }

    @Test
    void eventsWithoutRequiredKeysShouldNotEnqueue() {
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        OutboxContentEventPublisher publisher = new OutboxContentEventPublisher(
                new JacksonJsonCodec(JsonMappers.standard()),
                store,
                TOPIC
        );

        publisher.publishPostPublished(null);
        publisher.publishPostPublished(new PostPayload());
        publisher.publishCommentCreated(new CommentPayload());
        publisher.publishModerationActionApplied(new ModerationPayload());

        verifyNoInteractions(store);
    }

    @Test
    void postUpdatedAndModerationEventsShouldUseShortUuidV7EventIds() throws Exception {
        ObjectMapper objectMapper = JsonMappers.standard();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        UUID postId = uuid(303);
        UUID toUserId = uuid(404);
        OutboxContentEventPublisher publisher = new OutboxContentEventPublisher(
                new JacksonJsonCodec(JsonMappers.standard()),
                store,
                TOPIC
        );

        publisher.publishPostUpdated(postPayload(postId));
        publisher.publishModerationActionApplied(moderationPayload(toUserId));

        ArgumentCaptor<String> eventIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store, times(2)).enqueue(
                eventIdCaptor.capture(),
                eq(TOPIC),
                keyCaptor.capture(),
                payloadCaptor.capture()
        );
        List<String> eventIds = eventIdCaptor.getAllValues();
        assertThat(eventIds.get(0)).startsWith("ce:post:updated:");
        assertThat(eventIds.get(0).length()).isLessThanOrEqualTo(64);
        assertThat(eventIds.get(1)).startsWith("ce:moderation:");
        assertThat(eventIds.get(1).length()).isLessThanOrEqualTo(64);
        assertThat(keyCaptor.getAllValues()).containsExactly(postId.toString(), toUserId.toString());
        assertThat(objectMapper.readTree(payloadCaptor.getAllValues().get(0)).path("eventId").asText())
                .isEqualTo(eventIds.get(0));
        assertThat(objectMapper.readTree(payloadCaptor.getAllValues().get(1)).path("eventId").asText())
                .isEqualTo(eventIds.get(1));
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
        OutboxContentEventPublisher publisher = new OutboxContentEventPublisher(failingJsonCodec, store, TOPIC);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(505));
        payload.setCreateTime(Instant.EPOCH);

        assertThatThrownBy(() -> publisher.publishPostPublished(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload serialization failed");
        verifyNoInteractions(store);
    }

    private static PostPayload postPayload(UUID postId) {
        PostPayload payload = new PostPayload();
        payload.setPostId(postId);
        payload.setCreateTime(Instant.EPOCH);
        payload.setUpdateTime(Instant.EPOCH);
        return payload;
    }

    private static CommentPayload commentPayload(UUID commentId) {
        CommentPayload payload = new CommentPayload();
        payload.setCommentId(commentId);
        payload.setCreateTime(Instant.EPOCH);
        return payload;
    }

    private static ModerationPayload moderationPayload(UUID toUserId) {
        ModerationPayload payload = new ModerationPayload();
        payload.setToUserId(toUserId);
        payload.setCreateTime(Instant.EPOCH);
        return payload;
    }
}
