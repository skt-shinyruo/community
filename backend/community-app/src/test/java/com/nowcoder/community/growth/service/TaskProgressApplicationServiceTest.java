package com.nowcoder.community.growth.service;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskProgressApplicationServiceTest {

    @Test
    void postPublishedShouldTranslateToOwnerTaskProgressCommand() {
        TaskProgressService taskProgressService = mock(TaskProgressService.class);
        GrowthBusinessTimeService businessTimeService = mock(GrowthBusinessTimeService.class);
        TaskProgressApplicationService service = new TaskProgressApplicationService(taskProgressService, businessTimeService);
        Instant createTime = Instant.parse("2026-03-22T08:15:30Z");
        when(businessTimeService.dateOf(createTime)).thenReturn(LocalDate.of(2026, 3, 22));

        service.triggerPostPublished(uuid(100), uuid(7), createTime);

        verify(taskProgressService).processEvent(
                uuid(7),
                ContentEventTypes.POST_PUBLISHED,
                "post-published:" + uuid(100),
                LocalDate.of(2026, 3, 22)
        );
    }

    @Test
    void commentCreatedShouldUseCommentCreateTimeAsBizDate() {
        TaskProgressService taskProgressService = mock(TaskProgressService.class);
        GrowthBusinessTimeService businessTimeService = mock(GrowthBusinessTimeService.class);
        TaskProgressApplicationService service = new TaskProgressApplicationService(taskProgressService, businessTimeService);
        Instant createTime = Instant.parse("2026-03-22T09:00:00Z");
        when(businessTimeService.dateOf(createTime)).thenReturn(LocalDate.of(2026, 3, 22));

        CommentPayload payload = new CommentPayload();
        payload.setCommentId(uuid(200));
        payload.setUserId(uuid(3));
        payload.setCreateTime(createTime);

        service.triggerCommentCreated(payload);

        verify(taskProgressService).processEvent(
                uuid(3),
                ContentEventTypes.COMMENT_CREATED,
                "comment-created:" + uuid(200),
                LocalDate.of(2026, 3, 22)
        );
    }

    @Test
    void likeCreatedShouldTriggerEntityOwnerProgress() {
        TaskProgressService taskProgressService = mock(TaskProgressService.class);
        GrowthBusinessTimeService businessTimeService = mock(GrowthBusinessTimeService.class);
        TaskProgressApplicationService service = new TaskProgressApplicationService(taskProgressService, businessTimeService);
        Instant createTime = Instant.parse("2026-03-22T10:30:00Z");
        when(businessTimeService.dateOf(createTime)).thenReturn(LocalDate.of(2026, 3, 22));

        LikePayload payload = new LikePayload();
        payload.setActorUserId(uuid(1));
        payload.setEntityUserId(uuid(2));
        payload.setCreateTime(createTime);

        service.triggerLikeCreated("like-created-event", payload);

        verify(taskProgressService).processEvent(
                uuid(2),
                SocialEventTypes.LIKE_CREATED,
                "like-created-event",
                LocalDate.of(2026, 3, 22)
        );
    }

    @Test
    void selfLikeShouldRemainNoOp() {
        TaskProgressService taskProgressService = mock(TaskProgressService.class);
        GrowthBusinessTimeService businessTimeService = mock(GrowthBusinessTimeService.class);
        TaskProgressApplicationService service = new TaskProgressApplicationService(taskProgressService, businessTimeService);

        LikePayload payload = new LikePayload();
        payload.setActorUserId(uuid(9));
        payload.setEntityUserId(uuid(9));
        payload.setCreateTime(Instant.parse("2026-03-22T10:30:00Z"));

        service.triggerLikeCreated("like-created-event", payload);

        verifyNoInteractions(taskProgressService);
    }
}
