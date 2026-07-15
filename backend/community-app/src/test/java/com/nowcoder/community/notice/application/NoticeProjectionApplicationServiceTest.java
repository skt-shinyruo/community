package com.nowcoder.community.notice.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.notice.application.command.CreateNoticeCommand;
import com.nowcoder.community.notice.application.command.ProjectNoticeCommand;
import com.nowcoder.community.notice.domain.service.NoticeProjectionDomainService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NoticeProjectionApplicationServiceTest {

    @Test
    void duplicateCommentEventShouldCreateOneNotice() {
        NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
        NoticeProjectionEventRecorder eventRecorder = mock(NoticeProjectionEventRecorder.class);
        when(eventRecorder.tryRecord("evt-comment-duplicate")).thenReturn(true, false);
        NoticeProjectionApplicationService service = projectionService(noticeService, eventRecorder);
        ProjectNoticeCommand command = commentCommand("evt-comment-duplicate", uuid(100), uuid(9));

        service.projectReliably(command);
        service.projectReliably(command);

        verify(eventRecorder, times(2)).tryRecord("evt-comment-duplicate");
        verify(noticeService, times(1)).createNotice(any(CreateNoticeCommand.class));
    }

    @Test
    void replayIdentityShouldBeSourceEventIdEvenWhenPayloadChanges() {
        NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
        NoticeProjectionEventRecorder eventRecorder = mock(NoticeProjectionEventRecorder.class);
        when(eventRecorder.tryRecord("event-1")).thenReturn(true, false);
        NoticeProjectionApplicationService service = projectionService(noticeService, eventRecorder);

        service.projectReliably(commentCommand("event-1", uuid(100), uuid(9)));
        service.projectReliably(commentCommand("event-1", uuid(101), uuid(10)));

        verify(eventRecorder, times(2)).tryRecord("event-1");
        verify(noticeService, times(1)).createNotice(any(CreateNoticeCommand.class));
    }

    @Test
    void noticeCreationFailureShouldPropagateForKafkaRetry() {
        NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
        NoticeProjectionEventRecorder eventRecorder = mock(NoticeProjectionEventRecorder.class);
        when(eventRecorder.tryRecord("evt-comment-fails")).thenReturn(true);
        doThrow(new IllegalStateException("notice insert failed"))
                .when(noticeService).createNotice(any(CreateNoticeCommand.class));
        NoticeProjectionApplicationService service = projectionService(noticeService, eventRecorder);

        assertThatThrownBy(() -> service.projectReliably(commentCommand("evt-comment-fails", uuid(100), uuid(9))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("notice insert failed");

        verify(eventRecorder).tryRecord("evt-comment-fails");
    }

    @Test
    void blankSourceEventIdShouldBeRejectedBeforeRecording() {
        NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
        NoticeProjectionEventRecorder eventRecorder = mock(NoticeProjectionEventRecorder.class);
        NoticeProjectionApplicationService service = projectionService(noticeService, eventRecorder);

        assertThatThrownBy(() -> service.projectReliably(commentCommand("  ", uuid(100), uuid(9))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("notice projection source event id is blank");

        verifyNoInteractions(eventRecorder);
        verify(noticeService, never()).createNotice(any(CreateNoticeCommand.class));
    }

    @Test
    void commentProjectionShouldPreserveTopicAndContentJsonShape() {
        NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
        NoticeProjectionEventRecorder eventRecorder = mock(NoticeProjectionEventRecorder.class);
        when(eventRecorder.tryRecord("evt-comment-json")).thenReturn(true);
        NoticeProjectionApplicationService service = projectionService(noticeService, eventRecorder);

        service.projectReliably(commentCommand("evt-comment-json", uuid(100), uuid(9)));

        CreateNoticeCommand notice = capturedNotice(noticeService);
        assertThat(notice.toUserId()).isEqualTo(uuid(9));
        assertThat(notice.noticeTopic()).isEqualTo("comment");
        assertThat(notice.sourceEventType()).isEqualTo("CommentCreated");
        assertThat(notice.sourceRelationKey()).isNull();
        JsonNode content = jsonCodec().readTree(notice.contentJson());
        assertThat(content.path("eventId").asText()).isEqualTo("evt-comment-json");
        assertThat(content.path("type").asText()).isEqualTo("CommentCreated");
        assertThat(content.path("payload").path("commentId").asText()).isEqualTo(uuid(10).toString());
        assertThat(content.path("payload").path("postId").asText()).isEqualTo(uuid(100).toString());
        assertThat(content.path("payload").path("targetUserId").asText()).isEqualTo(uuid(9).toString());
        assertThat(content.path("payload").path("content").asText()).isEqualTo("hello");
    }

    @Test
    void moderationAndFollowCommandsShouldUseTheirNoticeTopics() {
        NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
        NoticeProjectionEventRecorder eventRecorder = mock(NoticeProjectionEventRecorder.class);
        when(eventRecorder.tryRecord("evt-moderation")).thenReturn(true);
        when(eventRecorder.tryRecord("evt-follow")).thenReturn(true);
        NoticeProjectionApplicationService service = projectionService(noticeService, eventRecorder);

        service.projectReliably(new ProjectNoticeCommand.ModerationApplied(
                "evt-moderation", 1L, "ModerationActionApplied", uuid(20), "report", uuid(9), uuid(1),
                EntityTypes.POST, uuid(100), "delete", "spam", 60, Instant.EPOCH));
        service.projectReliably(new ProjectNoticeCommand.FollowCreated(
                "evt-follow", 2L, "FollowCreated", uuid(1), EntityTypes.USER, uuid(9), uuid(9), Instant.EPOCH));

        ArgumentCaptor<CreateNoticeCommand> captor = ArgumentCaptor.forClass(CreateNoticeCommand.class);
        verify(noticeService, times(2)).createNotice(captor.capture());
        assertThat(captor.getAllValues()).extracting(CreateNoticeCommand::noticeTopic)
                .containsExactly("moderation", "follow");
    }

    @Test
    void likeCreatedShouldPreserveRelationKeyAndBeIdempotent() {
        NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
        NoticeProjectionEventRecorder eventRecorder = mock(NoticeProjectionEventRecorder.class);
        when(eventRecorder.tryRecord("evt-like-created")).thenReturn(true, false);
        NoticeProjectionApplicationService service = projectionService(noticeService, eventRecorder);
        String relationKey = "like:" + uuid(44) + ":3:" + uuid(100);
        ProjectNoticeCommand command = new ProjectNoticeCommand.LikeCreated(
                "evt-like-created", 21L, "LikeCreated", uuid(44), EntityTypes.POST,
                uuid(100), uuid(55), uuid(100), relationKey);

        service.projectReliably(command);
        service.projectReliably(command);

        CreateNoticeCommand notice = capturedNotice(noticeService);
        verify(eventRecorder, times(2)).tryRecord("evt-like-created");
        assertThat(notice.toUserId()).isEqualTo(uuid(55));
        assertThat(notice.noticeTopic()).isEqualTo("like");
        assertThat(notice.sourceEventType()).isEqualTo("LikeCreated");
        assertThat(notice.sourceRelationKey()).isEqualTo(relationKey);
        JsonNode payload = jsonCodec().readTree(notice.contentJson()).path("payload");
        assertThat(payload.path("actorUserId").asText()).isEqualTo(uuid(44).toString());
        assertThat(payload.path("relationKey").asText()).isEqualTo(relationKey);
    }

    @Test
    void likeRemovedShouldRevokeOnceForDuplicateEventId() {
        NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
        NoticeProjectionEventRecorder eventRecorder = mock(NoticeProjectionEventRecorder.class);
        when(eventRecorder.tryRecord("evt-like-removed")).thenReturn(true, false);
        NoticeProjectionApplicationService service = projectionService(noticeService, eventRecorder);
        ProjectNoticeCommand command = likeRemovedCommand("evt-like-removed");

        service.projectReliably(command);
        service.projectReliably(command);

        verify(eventRecorder, times(2)).tryRecord("evt-like-removed");
        verify(noticeService, times(1)).revokeLikeNotice(uuid(55), "like:actor:3:entity");
        verify(noticeService, never()).createNotice(any(CreateNoticeCommand.class));
    }

    @Test
    void blankLikeRemovedEventIdShouldBeRejectedBeforeRevoking() {
        NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
        NoticeProjectionEventRecorder eventRecorder = mock(NoticeProjectionEventRecorder.class);
        NoticeProjectionApplicationService service = projectionService(noticeService, eventRecorder);

        assertThatThrownBy(() -> service.projectReliably(likeRemovedCommand(" ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("notice projection source event id is blank");

        verifyNoInteractions(eventRecorder);
        verify(noticeService, never()).revokeLikeNotice(any(), any());
    }

    @Test
    void projectionDisabledShouldSkipRecordingAndSideEffects() {
        NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
        NoticeProjectionEventRecorder eventRecorder = mock(NoticeProjectionEventRecorder.class);
        NoticePolicyProperties properties = new NoticePolicyProperties();
        properties.setProjectionEnabled(false);
        NoticeProjectionApplicationService service = new NoticeProjectionApplicationService(
                jsonCodec(), noticeService, new NoticeProjectionDomainService(), properties, eventRecorder);

        service.projectReliably(commentCommand("evt-disabled", uuid(100), uuid(9)));

        verifyNoInteractions(noticeService, eventRecorder);
    }

    @Test
    void nullCommandShouldBeRejected() {
        assertThatThrownBy(() -> projectionService(
                mock(NoticeApplicationService.class), mock(NoticeProjectionEventRecorder.class)).projectReliably(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    private static NoticeProjectionApplicationService projectionService(
            NoticeApplicationService noticeService,
            NoticeProjectionEventRecorder eventRecorder
    ) {
        return new NoticeProjectionApplicationService(
                jsonCodec(), noticeService, new NoticeProjectionDomainService(), new NoticePolicyProperties(), eventRecorder);
    }

    private static ProjectNoticeCommand commentCommand(String eventId, java.util.UUID postId, java.util.UUID targetUserId) {
        return new ProjectNoticeCommand.CommentCreated(
                eventId, 42L, "CommentCreated", uuid(10), postId, uuid(1), EntityTypes.POST,
                postId, targetUserId, "hello", Instant.parse("2026-07-06T00:00:00Z"));
    }

    private static ProjectNoticeCommand likeRemovedCommand(String eventId) {
        return new ProjectNoticeCommand.LikeRemoved(
                eventId, 11L, "LikeRemoved", uuid(44), EntityTypes.POST,
                uuid(100), uuid(55), uuid(100), "like:actor:3:entity");
    }

    private static CreateNoticeCommand capturedNotice(NoticeApplicationService noticeService) {
        ArgumentCaptor<CreateNoticeCommand> captor = ArgumentCaptor.forClass(CreateNoticeCommand.class);
        verify(noticeService).createNotice(captor.capture());
        return captor.getValue();
    }

    private static JsonCodec jsonCodec() {
        return new JacksonJsonCodec(JsonMappers.standard());
    }
}
