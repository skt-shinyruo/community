package com.nowcoder.community.growth.application;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TaskProgressOutboxDispatchApplicationServiceTest {

    private static final String POST_TOPIC = "custom.growth.task.post-published";
    private static final String COMMENT_TOPIC = "custom.growth.task.comment-created";
    private static final String LIKE_TOPIC = "custom.growth.task.like-created";
    private static final String LIKE_REMOVED_TOPIC = "custom.growth.task.like-removed";

    private final TaskProgressKafkaDispatchPort dispatchPort = mock(TaskProgressKafkaDispatchPort.class);
    private final TaskProgressOutboxDispatchApplicationService service =
            new TaskProgressOutboxDispatchApplicationService(
                    new JacksonJsonCodec(JsonMappers.standard()),
                    dispatchPort,
                    POST_TOPIC,
                    COMMENT_TOPIC,
                    LIKE_TOPIC,
                    LIKE_REMOVED_TOPIC
            );

    @Test
    void dispatchPostPublishedShouldSendTypedPayloadThroughPort() {
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(100));
        payload.setUserId(uuid(7));
        payload.setCreateTime(Instant.parse("2026-05-18T08:30:00Z"));

        service.dispatchPostPublished(" ", JsonMappers.standard().valueToTree(payload).toString());

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(dispatchPort).send(org.mockito.ArgumentMatchers.eq(POST_TOPIC), org.mockito.ArgumentMatchers.eq(uuid(7).toString()), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).isInstanceOf(PostPayload.class);
        assertThat(((PostPayload) payloadCaptor.getValue()).getPostId()).isEqualTo(uuid(100));
    }

    @Test
    void dispatchCommentCreatedShouldSendTypedPayloadThroughPort() {
        CommentPayload payload = new CommentPayload();
        payload.setCommentId(uuid(200));
        payload.setUserId(uuid(3));
        payload.setCreateTime(Instant.parse("2026-05-18T09:30:00Z"));

        service.dispatchCommentCreated("comment-key", JsonMappers.standard().valueToTree(payload).toString());

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(dispatchPort).send(org.mockito.ArgumentMatchers.eq(COMMENT_TOPIC), org.mockito.ArgumentMatchers.eq("comment-key"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).isInstanceOf(CommentPayload.class);
        assertThat(((CommentPayload) payloadCaptor.getValue()).getCommentId()).isEqualTo(uuid(200));
    }

    @Test
    void dispatchLikeCreatedShouldSendTypedPayloadThroughPort() {
        LikePayload payload = new LikePayload();
        payload.setActorUserId(uuid(1));
        payload.setEntityType(POST);
        payload.setEntityId(uuid(100));
        payload.setEntityUserId(uuid(2));
        payload.setCreateTime(Instant.parse("2026-05-18T10:30:00Z"));

        service.dispatchLikeCreated("", JsonMappers.standard().valueToTree(payload).toString());

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(dispatchPort).send(org.mockito.ArgumentMatchers.eq(LIKE_TOPIC), org.mockito.ArgumentMatchers.eq(uuid(2).toString()), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).isInstanceOf(LikePayload.class);
        assertThat(((LikePayload) payloadCaptor.getValue()).getEntityId()).isEqualTo(uuid(100));
    }

    @Test
    void dispatchLikeRemovedShouldSendTypedPayloadThroughPort() {
        LikePayload payload = new LikePayload();
        payload.setActorUserId(uuid(1));
        payload.setEntityType(POST);
        payload.setEntityId(uuid(100));
        payload.setEntityUserId(uuid(2));
        payload.setRelationKey("like:" + uuid(1) + ":" + POST + ":" + uuid(100));
        payload.setCreateTime(Instant.parse("2026-05-18T10:30:00Z"));

        service.dispatchLikeRemoved("like-removed-key", JsonMappers.standard().valueToTree(payload).toString());

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(dispatchPort).send(org.mockito.ArgumentMatchers.eq(LIKE_REMOVED_TOPIC), org.mockito.ArgumentMatchers.eq("like-removed-key"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).isInstanceOf(LikePayload.class);
        assertThat(((LikePayload) payloadCaptor.getValue()).getRelationKey()).isEqualTo("like:" + uuid(1) + ":" + POST + ":" + uuid(100));
    }

    @Test
    void dispatchShouldIgnoreBlankPayloadAndMissingRequiredFields() {
        service.dispatchPostPublished("key", " ");
        service.dispatchPostPublished("key", "{}");
        service.dispatchCommentCreated("key", "{}");
        service.dispatchLikeCreated("key", "{}");
        service.dispatchLikeRemoved("key", "{}");

        verifyNoInteractions(dispatchPort);
    }

    @Test
    void dispatchShouldFailMalformedJsonForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatchPostPublished("key", "{not-json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("growth task post outbox payload");
    }

    @Test
    void dispatchShouldPropagatePortFailureForOutboxRetry() {
        RuntimeException failure = new RuntimeException("kafka down");
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(100));
        payload.setUserId(uuid(7));
        payload.setCreateTime(Instant.parse("2026-05-18T08:30:00Z"));
        doThrow(failure).when(dispatchPort).send(org.mockito.ArgumentMatchers.eq(POST_TOPIC), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> service.dispatchPostPublished("key", JsonMappers.standard().valueToTree(payload).toString()))
                .isSameAs(failure);
    }
}
