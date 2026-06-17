package com.nowcoder.community.content.application;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

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

    private static final String KAFKA_TOPIC = "custom.content.events";

    private final JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());
    private final ContentEventKafkaDispatchPort dispatchPort = mock(ContentEventKafkaDispatchPort.class);
    private final ContentEventDispatchApplicationService service =
            new ContentEventDispatchApplicationService(jsonCodec, dispatchPort, KAFKA_TOPIC);

    @Test
    void serviceShouldOnlyLoadForContentOutboxKafkaPublisher() {
        ConditionalOnExpression conditional = ContentEventDispatchApplicationService.class.getAnnotation(ConditionalOnExpression.class);

        assertThat(conditional).isNotNull();
        assertThat(conditional.value()).isEqualTo(
                "'${content.events.publisher:outbox-kafka}' == 'outbox-kafka' && '${events.outbox.enabled:true}' == 'true'"
        );
    }

    @Test
    void dispatchShouldConvertPostPayloadAndSendThroughPort() {
        UUID postId = uuid(101);

        service.dispatch(postId.toString(), toJson(new ContentContractEvent(
                "content:PostPublished:" + postId,
                ContentEventTypes.POST_PUBLISHED,
                postPayload(postId)
        )));

        ArgumentCaptor<ContentContractEvent> eventCaptor = ArgumentCaptor.forClass(ContentContractEvent.class);
        verify(dispatchPort).send(eq(KAFKA_TOPIC), eq(postId.toString()), eventCaptor.capture());
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

        service.dispatch(commentId.toString(), toJson(new ContentContractEvent(
                "content:CommentCreated:" + commentId,
                ContentEventTypes.COMMENT_CREATED,
                commentPayload(commentId)
        )));
        service.dispatch(toUserId.toString(), toJson(new ContentContractEvent(
                "content:ModerationActionApplied:" + toUserId,
                ContentEventTypes.MODERATION_ACTION_APPLIED,
                moderationPayload(toUserId)
        )));

        ArgumentCaptor<ContentContractEvent> eventCaptor = ArgumentCaptor.forClass(ContentContractEvent.class);
        verify(dispatchPort).send(eq(KAFKA_TOPIC), eq(commentId.toString()), eventCaptor.capture());
        verify(dispatchPort).send(eq(KAFKA_TOPIC), eq(toUserId.toString()), eventCaptor.capture());
        assertThat(eventCaptor.getAllValues().get(0).payload()).isInstanceOf(CommentPayload.class);
        assertThat(((CommentPayload) eventCaptor.getAllValues().get(0).payload()).getCommentId()).isEqualTo(commentId);
        assertThat(eventCaptor.getAllValues().get(1).payload()).isInstanceOf(ModerationPayload.class);
        assertThat(((ModerationPayload) eventCaptor.getAllValues().get(1).payload()).getToUserId()).isEqualTo(toUserId);
    }

    @Test
    void dispatchShouldRejectBlankOrNullPayloadForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatch("key", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload is blank");
        assertThatThrownBy(() -> service.dispatch("key", " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload is blank");
    }

    @Test
    void dispatchShouldRejectPayloadMissingEventIdForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatch("key", "{\"type\":\"PostPublished\",\"payload\":{\"postId\":\"" + uuid(404) + "\"}}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload missing eventId");

        verifyNoInteractions(dispatchPort);
    }

    @Test
    void dispatchShouldRejectPayloadMissingTypeForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatch("key", "{\"eventId\":\"event-1\",\"payload\":{\"postId\":\"" + uuid(404) + "\"}}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload missing type");
        assertThatThrownBy(() -> service.dispatch("key", "{\"eventId\":\"event-1\",\"type\":\" \",\"payload\":{\"postId\":\"" + uuid(404) + "\"}}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload missing type");

        verifyNoInteractions(dispatchPort);
    }

    @Test
    void dispatchShouldRejectKnownContentTypeMissingOrNullPayloadForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatch("key", "{\"eventId\":\"event-1\",\"type\":\"PostPublished\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload missing payload");
        assertThatThrownBy(() -> service.dispatch("key", "{\"eventId\":\"event-2\",\"type\":\"PostUpdated\",\"payload\":null}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload missing payload");
        assertThatThrownBy(() -> service.dispatch("key", "{\"eventId\":\"event-3\",\"type\":\"PostDeleted\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload missing payload");
        assertThatThrownBy(() -> service.dispatch("key", "{\"eventId\":\"event-4\",\"type\":\"CommentCreated\",\"payload\":null}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload missing payload");
        assertThatThrownBy(() -> service.dispatch("key", "{\"eventId\":\"event-5\",\"type\":\"CommentDeleted\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload missing payload");
        assertThatThrownBy(() -> service.dispatch("key", "{\"eventId\":\"event-6\",\"type\":\"ModerationActionApplied\",\"payload\":null}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload missing payload");

        verifyNoInteractions(dispatchPort);
    }

    @Test
    void dispatchShouldWrapPayloadDeserializationFailure() {
        assertThatThrownBy(() -> service.dispatch("key", "{not-json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event outbox payload deserialization failed");
    }

    @Test
    void dispatchShouldPropagatePortFailureForOutboxRetry() {
        RuntimeException failure = new RuntimeException("kafka down");
        UUID postId = uuid(505);
        String payloadJson = toJson(new ContentContractEvent(
                "content:PostPublished:" + postId,
                ContentEventTypes.POST_PUBLISHED,
                postPayload(postId)
        ));
        doThrow(failure).when(dispatchPort).send(eq(KAFKA_TOPIC), eq(postId.toString()), any());

        assertThatThrownBy(() -> service.dispatch(postId.toString(), payloadJson))
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
