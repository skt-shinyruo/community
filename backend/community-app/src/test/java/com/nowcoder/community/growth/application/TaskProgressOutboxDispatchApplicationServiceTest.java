package com.nowcoder.community.growth.application;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.growth.application.command.DispatchTaskProgressEventCommand;
import com.nowcoder.community.growth.application.command.TaskProgressDispatchKind;
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

    private final TaskProgressIntegrationEventDispatcher dispatcher = mock(TaskProgressIntegrationEventDispatcher.class);
    private final TaskProgressOutboxDispatchApplicationService service =
            new TaskProgressOutboxDispatchApplicationService(
                    new JacksonJsonCodec(JsonMappers.standard()),
                    dispatcher
            );

    @Test
    void dispatchPostPublishedShouldSendTypedPayloadThroughPort() {
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(100));
        payload.setUserId(uuid(7));
        payload.setCreateTime(Instant.parse("2026-05-18T08:30:00Z"));

        service.dispatch(new DispatchTaskProgressEventCommand(
                TaskProgressDispatchKind.POST_PUBLISHED,
                " ",
                JsonMappers.standard().valueToTree(payload).toString()
        ));

        ArgumentCaptor<PostPayload> payloadCaptor = ArgumentCaptor.forClass(PostPayload.class);
        verify(dispatcher).dispatchPostPublished(org.mockito.ArgumentMatchers.eq(uuid(7).toString()), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().getPostId()).isEqualTo(uuid(100));
    }

    @Test
    void dispatchCommentCreatedShouldSendTypedPayloadThroughPort() {
        CommentPayload payload = new CommentPayload();
        payload.setCommentId(uuid(200));
        payload.setUserId(uuid(3));
        payload.setCreateTime(Instant.parse("2026-05-18T09:30:00Z"));

        service.dispatch(new DispatchTaskProgressEventCommand(
                TaskProgressDispatchKind.COMMENT_CREATED,
                "comment-key",
                JsonMappers.standard().valueToTree(payload).toString()
        ));

        ArgumentCaptor<CommentPayload> payloadCaptor = ArgumentCaptor.forClass(CommentPayload.class);
        verify(dispatcher).dispatchCommentCreated(org.mockito.ArgumentMatchers.eq("comment-key"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().getCommentId()).isEqualTo(uuid(200));
    }

    @Test
    void dispatchLikeCreatedShouldSendTypedPayloadThroughPort() {
        LikePayload payload = new LikePayload();
        payload.setActorUserId(uuid(1));
        payload.setEntityType(POST);
        payload.setEntityId(uuid(100));
        payload.setEntityUserId(uuid(2));
        payload.setCreateTime(Instant.parse("2026-05-18T10:30:00Z"));

        service.dispatch(new DispatchTaskProgressEventCommand(
                TaskProgressDispatchKind.LIKE_CREATED,
                "",
                JsonMappers.standard().valueToTree(payload).toString()
        ));

        ArgumentCaptor<LikePayload> payloadCaptor = ArgumentCaptor.forClass(LikePayload.class);
        verify(dispatcher).dispatchLikeCreated(org.mockito.ArgumentMatchers.eq(uuid(2).toString()), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().getEntityId()).isEqualTo(uuid(100));
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

        service.dispatch(new DispatchTaskProgressEventCommand(
                TaskProgressDispatchKind.LIKE_REMOVED,
                "like-removed-key",
                JsonMappers.standard().valueToTree(payload).toString()
        ));

        ArgumentCaptor<LikePayload> payloadCaptor = ArgumentCaptor.forClass(LikePayload.class);
        verify(dispatcher).dispatchLikeRemoved(org.mockito.ArgumentMatchers.eq("like-removed-key"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().getRelationKey()).isEqualTo("like:" + uuid(1) + ":" + POST + ":" + uuid(100));
    }

    @Test
    void dispatchShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.dispatch(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void dispatchShouldIgnoreBlankPayloadAndMissingRequiredFields() {
        service.dispatch(new DispatchTaskProgressEventCommand(TaskProgressDispatchKind.POST_PUBLISHED, "key", " "));
        service.dispatch(new DispatchTaskProgressEventCommand(TaskProgressDispatchKind.POST_PUBLISHED, "key", "{}"));
        service.dispatch(new DispatchTaskProgressEventCommand(TaskProgressDispatchKind.COMMENT_CREATED, "key", "{}"));
        service.dispatch(new DispatchTaskProgressEventCommand(TaskProgressDispatchKind.LIKE_CREATED, "key", "{}"));
        service.dispatch(new DispatchTaskProgressEventCommand(TaskProgressDispatchKind.LIKE_REMOVED, "key", "{}"));

        verifyNoInteractions(dispatcher);
    }

    @Test
    void dispatchShouldFailMalformedJsonForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatch(new DispatchTaskProgressEventCommand(
                TaskProgressDispatchKind.POST_PUBLISHED,
                "key",
                "{not-json"
        )))
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
        doThrow(failure).when(dispatcher).dispatchPostPublished(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> service.dispatch(new DispatchTaskProgressEventCommand(
                TaskProgressDispatchKind.POST_PUBLISHED,
                "key",
                JsonMappers.standard().valueToTree(payload).toString()
        )))
                .isSameAs(failure);
    }
}
