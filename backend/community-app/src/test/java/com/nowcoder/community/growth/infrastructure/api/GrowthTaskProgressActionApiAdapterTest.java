package com.nowcoder.community.growth.infrastructure.api;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.growth.application.TaskProgressApplicationService;
import com.nowcoder.community.growth.application.command.TriggerCommentCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerLikeCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerPostPublishedCommand;
import com.nowcoder.community.social.contracts.event.LikePayload;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class GrowthTaskProgressActionApiAdapterTest {

    @Test
    void postPublishedShouldTranslateToOwnerTaskProgressCommand() {
        TaskProgressApplicationService applicationService = mock(TaskProgressApplicationService.class);
        GrowthTaskProgressActionApiAdapter service = new GrowthTaskProgressActionApiAdapter(applicationService);
        Instant createTime = Instant.parse("2026-03-22T08:15:30Z");

        service.triggerPostPublished(uuid(100), uuid(7), createTime);

        verify(applicationService).triggerPostPublished(new TriggerPostPublishedCommand(uuid(100), uuid(7), createTime));
    }

    @Test
    void commentCreatedShouldUseCommentCreateTimeAsBizDate() {
        TaskProgressApplicationService applicationService = mock(TaskProgressApplicationService.class);
        GrowthTaskProgressActionApiAdapter service = new GrowthTaskProgressActionApiAdapter(applicationService);
        Instant createTime = Instant.parse("2026-03-22T09:00:00Z");

        CommentPayload payload = new CommentPayload();
        payload.setCommentId(uuid(200));
        payload.setUserId(uuid(3));
        payload.setCreateTime(createTime);

        service.triggerCommentCreated(payload);

        verify(applicationService).triggerCommentCreated(new TriggerCommentCreatedCommand(uuid(200), uuid(3), createTime));
    }

    @Test
    void likeCreatedShouldTriggerEntityOwnerProgress() {
        TaskProgressApplicationService applicationService = mock(TaskProgressApplicationService.class);
        GrowthTaskProgressActionApiAdapter service = new GrowthTaskProgressActionApiAdapter(applicationService);
        Instant createTime = Instant.parse("2026-03-22T10:30:00Z");

        LikePayload payload = new LikePayload();
        payload.setActorUserId(uuid(1));
        payload.setEntityUserId(uuid(2));
        payload.setCreateTime(createTime);

        service.triggerLikeCreated("like-created-event", payload);

        verify(applicationService).triggerLikeCreated(new TriggerLikeCreatedCommand("like-created-event", uuid(1), uuid(2), createTime));
    }

    @Test
    void selfLikeShouldRemainNoOp() {
        TaskProgressApplicationService applicationService = mock(TaskProgressApplicationService.class);
        GrowthTaskProgressActionApiAdapter service = new GrowthTaskProgressActionApiAdapter(applicationService);

        LikePayload payload = new LikePayload();
        payload.setActorUserId(uuid(9));
        payload.setEntityUserId(uuid(9));
        payload.setCreateTime(Instant.parse("2026-03-22T10:30:00Z"));

        service.triggerLikeCreated("like-created-event", payload);

        verifyNoInteractions(applicationService);
    }
}
