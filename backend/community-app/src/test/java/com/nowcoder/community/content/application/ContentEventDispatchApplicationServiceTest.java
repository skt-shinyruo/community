package com.nowcoder.community.content.application;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.application.command.DispatchContentEventCommand;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
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

class ContentEventDispatchApplicationServiceTest {

    private final JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());
    private final ContentIntegrationEventDispatcher dispatcher = mock(ContentIntegrationEventDispatcher.class);
    private final ContentEventDispatchApplicationService service =
            new ContentEventDispatchApplicationService(jsonCodec, dispatcher);

    @Test
    void dispatchShouldConvertPostPayloadAndSendThroughPort() {
        UUID postId = uuid(101);

        service.dispatch(new DispatchContentEventCommand(postId.toString(), toJson(new ContentContractEvent(
                "content:PostPublished:" + postId,
                postId,
                "post",
                ContentEventTypes.POST_PUBLISHED,
                Instant.EPOCH,
                1L,
                postPayload(postId)
        ))));

        ArgumentCaptor<ContentContractEvent> eventCaptor = ArgumentCaptor.forClass(ContentContractEvent.class);
        verify(dispatcher).dispatch(eq(postId.toString()), eventCaptor.capture());
        ContentContractEvent event = eventCaptor.getValue();
        assertThat(event.eventId()).isEqualTo("content:PostPublished:" + postId);
        assertThat(event.type()).isEqualTo(ContentEventTypes.POST_PUBLISHED);
        assertThat(event.payload()).isInstanceOf(PostPayload.class);
        assertThat(((PostPayload) event.payload()).getPostId()).isEqualTo(postId);
    }

    @Test
    void dispatchShouldConvertCommentAndModerationPayloads() {
        UUID commentId = uuid(202);
        UUID toUserId = uuid(303);

        service.dispatch(new DispatchContentEventCommand(commentId.toString(), toJson(new ContentContractEvent(
                "content:CommentCreated:" + commentId,
                commentId,
                "comment",
                ContentEventTypes.COMMENT_CREATED,
                Instant.EPOCH,
                1L,
                commentPayload(commentId)
        ))));
        service.dispatch(new DispatchContentEventCommand(toUserId.toString(), toJson(new ContentContractEvent(
                "content:ModerationActionApplied:" + toUserId,
                toUserId,
                "user",
                ContentEventTypes.MODERATION_ACTION_APPLIED,
                Instant.EPOCH,
                1L,
                moderationPayload(toUserId)
        ))));

        ArgumentCaptor<ContentContractEvent> eventCaptor = ArgumentCaptor.forClass(ContentContractEvent.class);
        verify(dispatcher).dispatch(eq(commentId.toString()), eventCaptor.capture());
        verify(dispatcher).dispatch(eq(toUserId.toString()), eventCaptor.capture());
        assertThat(eventCaptor.getAllValues().get(0).payload()).isInstanceOf(CommentPayload.class);
        assertThat(((CommentPayload) eventCaptor.getAllValues().get(0).payload()).getCommentId()).isEqualTo(commentId);
        assertThat(eventCaptor.getAllValues().get(1).payload()).isInstanceOf(ModerationPayload.class);
        assertThat(((ModerationPayload) eventCaptor.getAllValues().get(1).payload()).getToUserId()).isEqualTo(toUserId);
    }

    @Test
    void dispatchShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.dispatch(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void dispatchShouldRejectBlankOrNullPayloadForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatch(new DispatchContentEventCommand("key", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload is blank");
        assertThatThrownBy(() -> service.dispatch(new DispatchContentEventCommand("key", " ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload is blank");
    }

    @Test
    void dispatchShouldRejectPayloadMissingEventIdForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatch(new DispatchContentEventCommand(
                "key",
                "{\"type\":\"PostPublished\",\"payload\":{\"postId\":\"" + uuid(404) + "\"}}"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload missing eventId");

        verifyNoInteractions(dispatcher);
    }

    @Test
    void dispatchShouldRejectPayloadMissingTypeForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatch(new DispatchContentEventCommand(
                "key",
                "{\"eventId\":\"event-1\",\"payload\":{\"postId\":\"" + uuid(404) + "\"}}"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload missing type");
        assertThatThrownBy(() -> service.dispatch(new DispatchContentEventCommand(
                "key",
                "{\"eventId\":\"event-1\",\"type\":\" \",\"payload\":{\"postId\":\"" + uuid(404) + "\"}}"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload missing type");

        verifyNoInteractions(dispatcher);
    }

    @Test
    void dispatchShouldRejectMissingAggregateMetadata() {
        String payloadJson = """
                {"eventId":"ce:1","type":"post.published","payload":{"postId":"%s"}}
                """.formatted(UUID.randomUUID());

        assertThatThrownBy(() -> service.dispatch(new DispatchContentEventCommand("post:" + UUID.randomUUID(), payloadJson)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("aggregateId");
    }

    @Test
    void dispatchShouldRejectMalformedAggregateIdForOutboxRetry() {
        String postId = uuid(405).toString();
        String payloadJson = """
                {"eventId":"ce:bad-aggregate","aggregateId":"not-a-uuid","aggregateType":"post","type":"%s","occurredAt":"1970-01-01T00:00:00Z","version":1,"payload":{"postId":"%s"}}
                """.formatted(ContentEventTypes.POST_PUBLISHED, postId);

        assertThatThrownBy(() -> service.dispatch(new DispatchContentEventCommand("post:" + postId, payloadJson)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload invalid aggregateId");

        verifyNoInteractions(dispatcher);
    }

    @Test
    void dispatchShouldRejectMalformedOccurredAtForOutboxRetry() {
        UUID postId = uuid(406);
        String payloadJson = """
                {"eventId":"ce:bad-occurred-at","aggregateId":"%s","aggregateType":"post","type":"%s","occurredAt":"not-an-instant","version":1,"payload":{"postId":"%s"}}
                """.formatted(postId, ContentEventTypes.POST_PUBLISHED, postId);

        assertThatThrownBy(() -> service.dispatch(new DispatchContentEventCommand("post:" + postId, payloadJson)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload invalid occurredAt");

        verifyNoInteractions(dispatcher);
    }

    @Test
    void dispatchShouldRejectKnownContentTypeMissingOrNullPayloadForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatch(new DispatchContentEventCommand("key", "{\"eventId\":\"event-1\",\"type\":\"PostPublished\"}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload missing payload");
        assertThatThrownBy(() -> service.dispatch(new DispatchContentEventCommand("key", "{\"eventId\":\"event-2\",\"type\":\"PostUpdated\",\"payload\":null}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload missing payload");
        assertThatThrownBy(() -> service.dispatch(new DispatchContentEventCommand("key", "{\"eventId\":\"event-3\",\"type\":\"PostDeleted\"}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload missing payload");
        assertThatThrownBy(() -> service.dispatch(new DispatchContentEventCommand("key", "{\"eventId\":\"event-4\",\"type\":\"CommentCreated\",\"payload\":null}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload missing payload");
        assertThatThrownBy(() -> service.dispatch(new DispatchContentEventCommand("key", "{\"eventId\":\"event-5\",\"type\":\"CommentDeleted\"}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload missing payload");
        assertThatThrownBy(() -> service.dispatch(new DispatchContentEventCommand("key", "{\"eventId\":\"event-6\",\"type\":\"ModerationActionApplied\",\"payload\":null}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload missing payload");

        verifyNoInteractions(dispatcher);
    }

    @Test
    void dispatchShouldWrapPayloadDeserializationFailure() {
        assertThatThrownBy(() -> service.dispatch(new DispatchContentEventCommand("key", "{not-json")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload deserialization failed");
    }

    @Test
    void dispatchShouldPropagatePortFailureForOutboxRetry() {
        RuntimeException failure = new RuntimeException("kafka down");
        UUID postId = uuid(505);
        String payloadJson = toJson(new ContentContractEvent(
                "content:PostPublished:" + postId,
                postId,
                "post",
                ContentEventTypes.POST_PUBLISHED,
                Instant.EPOCH,
                1L,
                postPayload(postId)
        ));
        doThrow(failure).when(dispatcher).dispatch(eq(postId.toString()), any());

        assertThatThrownBy(() -> service.dispatch(new DispatchContentEventCommand(postId.toString(), payloadJson)))
                .isSameAs(failure);
    }

    private String toJson(ContentContractEvent event) {
        return jsonCodec.toJson(event);
    }

    private static PostPayload postPayload(UUID postId) {
        PostPayload payload = new PostPayload();
        payload.setPostId(postId);
        return payload;
    }

    private static CommentPayload commentPayload(UUID commentId) {
        CommentPayload payload = new CommentPayload();
        payload.setCommentId(commentId);
        return payload;
    }

    private static ModerationPayload moderationPayload(UUID toUserId) {
        ModerationPayload payload = new ModerationPayload();
        payload.setToUserId(toUserId);
        return payload;
    }
}
