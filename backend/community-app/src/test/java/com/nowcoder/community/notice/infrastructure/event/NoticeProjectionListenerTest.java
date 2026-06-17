package com.nowcoder.community.notice.infrastructure.event;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.notice.application.NoticeProjectionApplicationService;
import com.nowcoder.community.notice.application.command.ProjectContentNoticeCommand;
import com.nowcoder.community.notice.application.command.ProjectSocialNoticeCommand;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class NoticeProjectionListenerTest {

    @Test
    void contentEventShouldAdaptToNoticeOwnedCommand() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionListener listener = new NoticeProjectionListener(applicationService);
        UUID targetUserId = uuid(9);
        UUID postId = uuid(100);

        CommentPayload payload = new CommentPayload();
        payload.setTargetUserId(targetUserId);
        payload.setPostId(postId);

        listener.onContentEvent(new ContentContractEvent("evt-comment-1", ContentEventTypes.COMMENT_CREATED, payload));

        ArgumentCaptor<ProjectContentNoticeCommand> captor = ArgumentCaptor.forClass(ProjectContentNoticeCommand.class);
        verify(applicationService).projectContentEvent(captor.capture());
        assertThat(captor.getValue().sourceEventId()).isEqualTo("evt-comment-1");
        assertThat(captor.getValue().eventType()).isEqualTo(ContentEventTypes.COMMENT_CREATED);
        assertThat(captor.getValue().payload()).isSameAs(payload);
    }

    @Test
    void socialEventShouldAdaptToNoticeOwnedCommand() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionListener listener = new NoticeProjectionListener(applicationService);
        UUID entityUserId = uuid(8);
        UUID entityId = uuid(200);

        LikePayload payload = new LikePayload();
        payload.setEntityUserId(entityUserId);
        payload.setEntityId(entityId);

        listener.onSocialEvent(new SocialContractEvent("evt-like-1", SocialEventTypes.LIKE_CREATED, payload));

        ArgumentCaptor<ProjectSocialNoticeCommand> captor = ArgumentCaptor.forClass(ProjectSocialNoticeCommand.class);
        verify(applicationService).projectSocialEvent(captor.capture());
        assertThat(captor.getValue().sourceEventId()).isEqualTo("evt-like-1");
        assertThat(captor.getValue().eventType()).isEqualTo(SocialEventTypes.LIKE_CREATED);
        assertThat(captor.getValue().payload()).isSameAs(payload);
    }

    @Test
    void nullEventsShouldBeIgnored() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionListener listener = new NoticeProjectionListener(applicationService);

        listener.onContentEvent(null);
        listener.onSocialEvent(null);

        verifyNoInteractions(applicationService);
    }
}
