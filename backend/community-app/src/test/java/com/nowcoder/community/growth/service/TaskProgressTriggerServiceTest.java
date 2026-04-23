package com.nowcoder.community.growth.service;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskProgressTriggerServiceTest {

    @Test
    void postPublishedShouldTriggerAuthorProgressThroughProjectionService() {
        TaskProgressService taskProgressService = mock(TaskProgressService.class);
        GrowthBusinessTimeService businessTimeService = mock(GrowthBusinessTimeService.class);
        TaskProgressTriggerService service = new TaskProgressTriggerService(
                new TaskProgressProjectionService(taskProgressService, businessTimeService)
        );
        UUID postId = uuid(100);
        UUID userId = uuid(7);
        Instant createTime = Instant.parse("2026-03-22T08:15:30Z");
        when(businessTimeService.dateOf(createTime)).thenReturn(LocalDate.of(2026, 3, 22));

        service.triggerPostPublished(postId, userId, createTime);

        verify(taskProgressService).processEvent(
                userId,
                ContentEventTypes.POST_PUBLISHED,
                "post-published:" + postId,
                LocalDate.of(2026, 3, 22)
        );
    }

    @Test
    void commentCreatedShouldTriggerCommentAuthorProgressThroughProjectionService() {
        TaskProgressService taskProgressService = mock(TaskProgressService.class);
        GrowthBusinessTimeService businessTimeService = mock(GrowthBusinessTimeService.class);
        TaskProgressTriggerService service = new TaskProgressTriggerService(
                new TaskProgressProjectionService(taskProgressService, businessTimeService)
        );
        UUID commentId = uuid(200);
        UUID userId = uuid(3);
        Instant createTime = Instant.parse("2026-03-22T09:00:00Z");
        when(businessTimeService.dateOf(createTime)).thenReturn(LocalDate.of(2026, 3, 22));

        CommentPayload payload = new CommentPayload();
        payload.setCommentId(commentId);
        payload.setUserId(userId);
        payload.setCreateTime(createTime);

        service.triggerCommentCreated(payload);

        verify(taskProgressService).processEvent(
                userId,
                ContentEventTypes.COMMENT_CREATED,
                "comment-created:" + commentId,
                LocalDate.of(2026, 3, 22)
        );
    }

    @Test
    void likeCreatedShouldTriggerEntityOwnerProgressThroughProjectionService() {
        TaskProgressService taskProgressService = mock(TaskProgressService.class);
        GrowthBusinessTimeService businessTimeService = mock(GrowthBusinessTimeService.class);
        TaskProgressTriggerService service = new TaskProgressTriggerService(
                new TaskProgressProjectionService(taskProgressService, businessTimeService)
        );
        UUID actorUserId = uuid(1);
        UUID entityUserId = uuid(2);
        Instant createTime = Instant.parse("2026-03-22T10:30:00Z");
        when(businessTimeService.dateOf(createTime)).thenReturn(LocalDate.of(2026, 3, 22));

        LikePayload payload = new LikePayload();
        payload.setActorUserId(actorUserId);
        payload.setEntityUserId(entityUserId);
        payload.setCreateTime(createTime);

        service.triggerLikeCreated("like-created-event", payload);

        verify(taskProgressService).processEvent(
                entityUserId,
                SocialEventTypes.LIKE_CREATED,
                "like-created-event",
                LocalDate.of(2026, 3, 22)
        );
    }

}
