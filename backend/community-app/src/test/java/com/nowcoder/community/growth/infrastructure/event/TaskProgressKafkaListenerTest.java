package com.nowcoder.community.growth.infrastructure.event;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.growth.application.TaskProgressApplicationService;
import com.nowcoder.community.growth.application.command.TriggerCommentCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerLikeCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerPostPublishedCommand;
import com.nowcoder.community.social.contracts.event.LikePayload;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TaskProgressKafkaListenerTest {

    @Test
    void postPublishedShouldEnterGrowthApplicationService() {
        TaskProgressApplicationService applicationService = mock(TaskProgressApplicationService.class);
        TaskProgressKafkaListener listener = new TaskProgressKafkaListener(applicationService);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(100));
        payload.setUserId(uuid(7));
        payload.setCreateTime(Instant.parse("2026-05-18T08:30:00Z"));

        listener.onPostPublished(payload);

        verify(applicationService).triggerPostPublished(new TriggerPostPublishedCommand(
                uuid(100),
                uuid(7),
                Instant.parse("2026-05-18T08:30:00Z")
        ));
    }

    @Test
    void commentCreatedShouldEnterGrowthApplicationService() {
        TaskProgressApplicationService applicationService = mock(TaskProgressApplicationService.class);
        TaskProgressKafkaListener listener = new TaskProgressKafkaListener(applicationService);
        CommentPayload payload = new CommentPayload();
        payload.setCommentId(uuid(200));
        payload.setUserId(uuid(3));
        payload.setCreateTime(Instant.parse("2026-05-18T09:30:00Z"));

        listener.onCommentCreated(payload);

        verify(applicationService).triggerCommentCreated(new TriggerCommentCreatedCommand(
                uuid(200),
                uuid(3),
                Instant.parse("2026-05-18T09:30:00Z")
        ));
    }

    @Test
    void likeCreatedShouldEnterGrowthApplicationServiceForEntityOwner() {
        TaskProgressApplicationService applicationService = mock(TaskProgressApplicationService.class);
        TaskProgressKafkaListener listener = new TaskProgressKafkaListener(applicationService);
        LikePayload payload = new LikePayload();
        payload.setActorUserId(uuid(1));
        payload.setEntityType(POST);
        payload.setEntityId(uuid(100));
        payload.setEntityUserId(uuid(2));
        payload.setCreateTime(Instant.parse("2026-05-18T10:30:00Z"));

        listener.onLikeCreated(payload);

        verify(applicationService).triggerLikeCreated(new TriggerLikeCreatedCommand(
                "like-created:" + uuid(1) + ":" + POST + ":" + uuid(100),
                uuid(1),
                uuid(2),
                Instant.parse("2026-05-18T10:30:00Z")
        ));
    }

    @Test
    void selfLikeShouldRemainNoOp() {
        TaskProgressApplicationService applicationService = mock(TaskProgressApplicationService.class);
        TaskProgressKafkaListener listener = new TaskProgressKafkaListener(applicationService);
        LikePayload payload = new LikePayload();
        payload.setActorUserId(uuid(1));
        payload.setEntityType(POST);
        payload.setEntityId(uuid(100));
        payload.setEntityUserId(uuid(1));
        payload.setCreateTime(Instant.parse("2026-05-18T10:30:00Z"));

        listener.onLikeCreated(payload);

        verifyNoInteractions(applicationService);
    }
}
