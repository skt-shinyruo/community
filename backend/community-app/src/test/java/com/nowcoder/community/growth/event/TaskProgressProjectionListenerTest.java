package com.nowcoder.community.growth.event;

import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.growth.event.payload.CheckInPayload;
import com.nowcoder.community.growth.service.GrowthBusinessTimeService;
import com.nowcoder.community.growth.service.TaskProgressService;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
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
        UUID userId = uuid(7);

        PostPayload payload = new PostPayload();
        payload.setUserId(userId);
        payload.setCreateTime(Instant.parse("2026-03-21T16:30:00Z"));

        listener.onContentEvent(new ContentContractEvent("post-evt-1", ContentEventTypes.POST_PUBLISHED, payload));

        verify(taskProgressService).processEvent(userId, ContentEventTypes.POST_PUBLISHED, "post-evt-1", LocalDate.of(2026, 3, 22));
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
        UUID actorUserId = uuid(2);
        UUID entityUserId = uuid(9);

        LikePayload payload = new LikePayload();
        payload.setActorUserId(actorUserId);
        payload.setEntityUserId(entityUserId);
        payload.setCreateTime(Instant.parse("2026-03-22T01:00:00Z"));

        listener.onSocialEvent(new SocialContractEvent("like-evt-1", SocialEventTypes.LIKE_CREATED, payload));

        verify(taskProgressService).processEvent(entityUserId, SocialEventTypes.LIKE_CREATED, "like-evt-1", LocalDate.of(2026, 3, 22));
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
        UUID userId = uuid(2);

        LikePayload payload = new LikePayload();
        payload.setActorUserId(userId);
        payload.setEntityUserId(userId);
        payload.setCreateTime(Instant.parse("2026-03-22T01:00:00Z"));

        listener.onSocialEvent(new SocialContractEvent("like-evt-1", SocialEventTypes.LIKE_CREATED, payload));

        verify(taskProgressService, never()).processEvent(org.mockito.ArgumentMatchers.any(UUID.class), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
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
        UUID userId = uuid(5);
        String eventId = "check-in:" + userId + ":2026-03-22";

        CheckInPayload payload = new CheckInPayload();
        payload.setUserId(userId);
        payload.setBizDate(LocalDate.of(2026, 3, 22));

        listener.onGrowthEvent(new GrowthLocalEvent(eventId, GrowthEventTypes.CHECK_IN_COMPLETED, payload));

        verify(taskProgressService).processEvent(userId, GrowthEventTypes.CHECK_IN_COMPLETED, eventId, LocalDate.of(2026, 3, 22));
    }
}
