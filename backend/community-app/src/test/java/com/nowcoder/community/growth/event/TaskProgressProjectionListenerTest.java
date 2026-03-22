package com.nowcoder.community.growth.event;

import com.nowcoder.community.content.event.ContentEventTypes;
import com.nowcoder.community.content.event.ContentLocalEvent;
import com.nowcoder.community.content.event.payload.PostPayload;
import com.nowcoder.community.growth.event.payload.CheckInPayload;
import com.nowcoder.community.growth.service.GrowthBusinessTimeService;
import com.nowcoder.community.growth.service.TaskProgressService;
import com.nowcoder.community.social.event.SocialEventTypes;
import com.nowcoder.community.social.event.SocialLocalEvent;
import com.nowcoder.community.social.event.payload.LikePayload;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TaskProgressProjectionListenerTest {

    @Test
    void postPublishedShouldAdvanceTaskProgressForAuthor() {
        TaskProgressService taskProgressService = mock(TaskProgressService.class);
        TaskProgressProjectionListener listener = new TaskProgressProjectionListener(
                taskProgressService,
                new GrowthBusinessTimeService(
                        Clock.fixed(Instant.parse("2026-03-22T00:00:00Z"), ZoneId.of("Asia/Shanghai")),
                        ZoneId.of("Asia/Shanghai")
                )
        );

        PostPayload payload = new PostPayload();
        payload.setUserId(7);
        payload.setCreateTime(Instant.parse("2026-03-21T16:30:00Z"));

        listener.onContentEvent(new ContentLocalEvent("post-evt-1", ContentEventTypes.POST_PUBLISHED, payload));

        verify(taskProgressService).processEvent(7, ContentEventTypes.POST_PUBLISHED, "post-evt-1", LocalDate.of(2026, 3, 22));
    }

    @Test
    void likeCreatedShouldAdvanceTaskProgressForEntityOwner() {
        TaskProgressService taskProgressService = mock(TaskProgressService.class);
        TaskProgressProjectionListener listener = new TaskProgressProjectionListener(
                taskProgressService,
                new GrowthBusinessTimeService(
                        Clock.fixed(Instant.parse("2026-03-22T00:00:00Z"), ZoneId.of("Asia/Shanghai")),
                        ZoneId.of("Asia/Shanghai")
                )
        );

        LikePayload payload = new LikePayload();
        payload.setActorUserId(2);
        payload.setEntityUserId(9);
        payload.setCreateTime(Instant.parse("2026-03-22T01:00:00Z"));

        listener.onSocialEvent(new SocialLocalEvent("like-evt-1", SocialEventTypes.LIKE_CREATED, payload));

        verify(taskProgressService).processEvent(9, SocialEventTypes.LIKE_CREATED, "like-evt-1", LocalDate.of(2026, 3, 22));
    }

    @Test
    void selfLikeShouldBeIgnored() {
        TaskProgressService taskProgressService = mock(TaskProgressService.class);
        TaskProgressProjectionListener listener = new TaskProgressProjectionListener(
                taskProgressService,
                new GrowthBusinessTimeService(
                        Clock.fixed(Instant.parse("2026-03-22T00:00:00Z"), ZoneId.of("Asia/Shanghai")),
                        ZoneId.of("Asia/Shanghai")
                )
        );

        LikePayload payload = new LikePayload();
        payload.setActorUserId(2);
        payload.setEntityUserId(2);
        payload.setCreateTime(Instant.parse("2026-03-22T01:00:00Z"));

        listener.onSocialEvent(new SocialLocalEvent("like-evt-1", SocialEventTypes.LIKE_CREATED, payload));

        verify(taskProgressService, never()).processEvent(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void checkInCompletedShouldAdvanceTaskProgressForSignedInUser() {
        TaskProgressService taskProgressService = mock(TaskProgressService.class);
        TaskProgressProjectionListener listener = new TaskProgressProjectionListener(
                taskProgressService,
                new GrowthBusinessTimeService(
                        Clock.fixed(Instant.parse("2026-03-22T00:00:00Z"), ZoneId.of("Asia/Shanghai")),
                        ZoneId.of("Asia/Shanghai")
                )
        );

        CheckInPayload payload = new CheckInPayload();
        payload.setUserId(5);
        payload.setBizDate(LocalDate.of(2026, 3, 22));

        listener.onGrowthEvent(new GrowthLocalEvent("check-in:5:2026-03-22", GrowthEventTypes.CHECK_IN_COMPLETED, payload));

        verify(taskProgressService).processEvent(5, GrowthEventTypes.CHECK_IN_COMPLETED, "check-in:5:2026-03-22", LocalDate.of(2026, 3, 22));
    }
}
