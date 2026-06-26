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
        NoticeProjectionApplicationService projectionService = projectionService(noticeService, mock(NoticeProjectionEventRecorder.class));

        LikePayload payload = new LikePayload();
        payload.setEntityUserId(UUID.fromString("00000000-0000-0000-0000-000000000055"));
        payload.setRelationKey("like:actor:3:entity");

        projectionService.projectSocialEventReliably(new ProjectSocialNoticeCommand(
                "evt-like-removed-1",
                SocialEventTypes.LIKE_REMOVED,
                payload
        ));

        verify(noticeService).revokeLikeNotice(payload.getEntityUserId(), payload.getRelationKey());
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
        return new ProjectContentNoticeCommand(eventId, ContentEventTypes.COMMENT_CREATED, payload);
    }

    private static JsonCodec jsonCodec() {
        return new JacksonJsonCodec(JsonMappers.standard());
    }
}
