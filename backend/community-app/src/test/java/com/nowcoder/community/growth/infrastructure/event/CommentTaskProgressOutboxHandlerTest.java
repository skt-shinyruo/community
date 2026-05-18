package com.nowcoder.community.growth.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.growth.application.TaskProgressApplicationService;
import com.nowcoder.community.growth.application.command.TriggerCommentCreatedCommand;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CommentTaskProgressOutboxHandlerTest {

    private static final String TOPIC = "custom.projection.growth.task.comment";

    @Test
    void handlerShouldTriggerCommentTaskProgressThroughGrowthApplicationService() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        TaskProgressApplicationService applicationService = mock(TaskProgressApplicationService.class);
        CommentTaskProgressOutboxHandler handler = new CommentTaskProgressOutboxHandler(objectMapper, applicationService, TOPIC);
        UUID commentId = uuid(200);
        UUID userId = uuid(3);
        Instant createTime = Instant.parse("2026-05-18T09:30:00Z");

        handler.handle(outboxEvent(objectMapper, commentId, userId, createTime));

        verify(applicationService).triggerCommentCreated(new TriggerCommentCreatedCommand(commentId, userId, createTime));
    }

    @Test
    void handlerShouldIgnoreBlankPayload() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        TaskProgressApplicationService applicationService = mock(TaskProgressApplicationService.class);
        CommentTaskProgressOutboxHandler handler = new CommentTaskProgressOutboxHandler(objectMapper, applicationService, TOPIC);

        handler.handle(new OutboxEvent(
                UUID.fromString("01965429-b34a-7000-8000-000000000041"),
                "comment-created:blank:growth_task",
                TOPIC,
                "key",
                " ",
                "PENDING",
                0,
                null,
                null,
                null,
                null
        ));

        verifyNoInteractions(applicationService);
    }

    private static OutboxEvent outboxEvent(
            ObjectMapper objectMapper,
            UUID commentId,
            UUID userId,
            Instant createTime
    ) throws Exception {
        CommentPayload payload = new CommentPayload();
        payload.setCommentId(commentId);
        payload.setUserId(userId);
        payload.setCreateTime(createTime);
        return new OutboxEvent(
                UUID.fromString("01965429-b34a-7000-8000-000000000042"),
                "comment-created:" + commentId + ":growth_task",
                TOPIC,
                userId.toString(),
                objectMapper.writeValueAsString(payload),
                "PENDING",
                0,
                null,
                null,
                null,
                null
        );
    }
}
