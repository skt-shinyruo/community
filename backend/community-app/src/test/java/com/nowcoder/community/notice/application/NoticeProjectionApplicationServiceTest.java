package com.nowcoder.community.notice.application;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.notice.application.command.CreateNoticeCommand;
import com.nowcoder.community.notice.application.command.ProjectContentNoticeCommand;
import com.nowcoder.community.notice.application.command.ProjectSocialNoticeCommand;
import com.nowcoder.community.notice.domain.service.NoticeProjectionDomainService;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
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
    void reliableContentProjectionShouldCreateOneNoticeForDuplicateEventId() {
        NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
        NoticeProjectionEventRecorder eventRecorder = mock(NoticeProjectionEventRecorder.class);
        when(eventRecorder.tryRecord("evt-comment-duplicate")).thenReturn(true, false);
        NoticeProjectionApplicationService projectionService = projectionService(noticeService, eventRecorder);
        ProjectContentNoticeCommand command = commentCommand("evt-comment-duplicate");

        projectionService.projectContentEventReliably(command);
        projectionService.projectContentEventReliably(command);

        verify(eventRecorder, times(2)).tryRecord("evt-comment-duplicate");
        verify(noticeService, times(1)).createNotice(any(CreateNoticeCommand.class));
    }

    @Test
    void replayedSourceEventShouldNotCreateDuplicateNotice() {
        NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
        NoticeProjectionEventRecorder eventRecorder = mock(NoticeProjectionEventRecorder.class);
        when(eventRecorder.tryRecord("event-1")).thenReturn(true, false);
        NoticeProjectionApplicationService projectionService = projectionService(noticeService, eventRecorder);
        ProjectContentNoticeCommand command = commentCommand("event-1");

        projectionService.projectContentEventReliably(command);
        projectionService.projectContentEventReliably(command);

        verify(eventRecorder, times(2)).tryRecord("event-1");
        verify(noticeService, times(1)).createNotice(any(CreateNoticeCommand.class));
    }

    @Test
    void reliableContentProjectionShouldPropagateNoticeCreationFailureForKafkaRetry() {
        NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
        NoticeProjectionEventRecorder eventRecorder = mock(NoticeProjectionEventRecorder.class);
        when(eventRecorder.tryRecord("evt-comment-fails")).thenReturn(true);
        doThrow(new IllegalStateException("notice insert failed"))
                .when(noticeService).createNotice(any(CreateNoticeCommand.class));
        NoticeProjectionApplicationService projectionService = projectionService(noticeService, eventRecorder);

        assertThatThrownBy(() -> projectionService.projectContentEventReliably(commentCommand("evt-comment-fails")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("notice insert failed");

        verify(eventRecorder).tryRecord("evt-comment-fails");
    }

    @Test
    void reliableContentProjectionShouldRejectNullSourceEventIdBeforeRecording() {
        NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
        NoticeProjectionEventRecorder eventRecorder = mock(NoticeProjectionEventRecorder.class);
        NoticeProjectionApplicationService projectionService = projectionService(noticeService, eventRecorder);

        assertThatThrownBy(() -> projectionService.projectContentEventReliably(commentCommand(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("notice projection source event id is blank");

        verifyNoInteractions(eventRecorder);
        verify(noticeService, never()).createNotice(any(CreateNoticeCommand.class));
    }

    @Test
    void reliableContentProjectionShouldRejectBlankSourceEventIdBeforeRecording() {
        NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
        NoticeProjectionEventRecorder eventRecorder = mock(NoticeProjectionEventRecorder.class);
        NoticeProjectionApplicationService projectionService = projectionService(noticeService, eventRecorder);

        assertThatThrownBy(() -> projectionService.projectContentEventReliably(commentCommand("  ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("notice projection source event id is blank");

        verifyNoInteractions(eventRecorder);
        verify(noticeService, never()).createNotice(any(CreateNoticeCommand.class));
    }

    @Test
    void shouldRevokeLikeNoticeOnLikeRemoved() {
        NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
        NoticeProjectionEventRecorder eventRecorder = mock(NoticeProjectionEventRecorder.class);
        when(eventRecorder.tryRecord("evt-like-removed-1")).thenReturn(true);
        NoticeProjectionApplicationService projectionService = projectionService(noticeService, eventRecorder);

        LikePayload payload = new LikePayload();
        payload.setEntityUserId(UUID.fromString("00000000-0000-0000-0000-000000000055"));
        payload.setRelationKey("like:actor:3:entity");

        projectionService.projectSocialEventReliably(new ProjectSocialNoticeCommand(
                "evt-like-removed-1",
                11L,
                SocialEventTypes.LIKE_REMOVED,
                payload
        ));

        verify(eventRecorder).tryRecord("evt-like-removed-1");
        verify(noticeService).revokeLikeNotice(payload.getEntityUserId(), payload.getRelationKey());
    }

    @Test
    void reliableLikeRemovedProjectionShouldRejectBlankSourceEventIdBeforeRevoking() {
        NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
        NoticeProjectionEventRecorder eventRecorder = mock(NoticeProjectionEventRecorder.class);
        NoticeProjectionApplicationService projectionService = projectionService(noticeService, eventRecorder);
        LikePayload payload = new LikePayload();
        payload.setEntityUserId(uuid(55));
        payload.setRelationKey("like:actor:3:entity");

        assertThatThrownBy(() -> projectionService.projectSocialEventReliably(new ProjectSocialNoticeCommand(
                " ",
                11L,
                SocialEventTypes.LIKE_REMOVED,
                payload
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("notice projection source event id is blank");

        verifyNoInteractions(eventRecorder);
        verify(noticeService, never()).revokeLikeNotice(any(), any());
    }

    @Test
    void reliableLikeRemovedProjectionShouldRevokeOnceForDuplicateEventId() {
        NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
        NoticeProjectionEventRecorder eventRecorder = mock(NoticeProjectionEventRecorder.class);
        when(eventRecorder.tryRecord("evt-like-removed-duplicate")).thenReturn(true, false);
        NoticeProjectionApplicationService projectionService = projectionService(noticeService, eventRecorder);
        LikePayload payload = new LikePayload();
        payload.setEntityUserId(uuid(55));
        payload.setRelationKey("like:actor:3:entity");
        ProjectSocialNoticeCommand command = new ProjectSocialNoticeCommand(
                "evt-like-removed-duplicate",
                12L,
                SocialEventTypes.LIKE_REMOVED,
                payload
        );

        projectionService.projectSocialEventReliably(command);
        projectionService.projectSocialEventReliably(command);

        verify(eventRecorder, times(2)).tryRecord("evt-like-removed-duplicate");
        verify(noticeService, times(1)).revokeLikeNotice(payload.getEntityUserId(), payload.getRelationKey());
    }

    @Test
    void reliableContentProjectionShouldSkipWhenProjectionDisabled() {
        NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
        NoticeProjectionEventRecorder eventRecorder = mock(NoticeProjectionEventRecorder.class);
        NoticePolicyProperties properties = new NoticePolicyProperties();
        properties.getChannels().setInAppEnabled(true);
        properties.setProjectionEnabled(false);
        NoticeProjectionApplicationService projectionService = new NoticeProjectionApplicationService(
                jsonCodec(),
                noticeService,
                new NoticeProjectionDomainService(),
                properties,
                eventRecorder
        );

        projectionService.projectContentEventReliably(commentCommand("evt-comment-disabled"));

        verifyNoInteractions(noticeService, eventRecorder);
    }

    @Test
    void projectContentEventShouldRejectNullCommand() {
        assertThatThrownBy(() -> projectionService(mock(NoticeApplicationService.class), mock(NoticeProjectionEventRecorder.class))
                .projectContentEvent(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void projectSocialEventShouldRejectNullCommand() {
        assertThatThrownBy(() -> projectionService(mock(NoticeApplicationService.class), mock(NoticeProjectionEventRecorder.class))
                .projectSocialEvent(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void projectContentEventReliablyShouldRejectNullCommand() {
        assertThatThrownBy(() -> projectionService(mock(NoticeApplicationService.class), mock(NoticeProjectionEventRecorder.class))
                .projectContentEventReliably(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void projectSocialEventReliablyShouldRejectNullCommand() {
        assertThatThrownBy(() -> projectionService(mock(NoticeApplicationService.class), mock(NoticeProjectionEventRecorder.class))
                .projectSocialEventReliably(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    private static NoticeProjectionApplicationService projectionService(
            NoticeApplicationService noticeService,
            NoticeProjectionEventRecorder eventRecorder
    ) {
        return new NoticeProjectionApplicationService(
                jsonCodec(),
                noticeService,
                new NoticeProjectionDomainService(),
                new NoticePolicyProperties(),
                eventRecorder
        );
    }

    private static ProjectContentNoticeCommand commentCommand(String eventId) {
        CommentPayload payload = new CommentPayload();
        payload.setTargetUserId(uuid(9));
        payload.setPostId(uuid(100));
        return new ProjectContentNoticeCommand(eventId, 42L, ContentEventTypes.COMMENT_CREATED, payload);
    }

    private static JsonCodec jsonCodec() {
        return new JacksonJsonCodec(JsonMappers.standard());
    }
}
