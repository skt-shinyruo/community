package com.nowcoder.community.growth.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.growth.application.TaskProgressApplicationService;
import com.nowcoder.community.growth.application.command.TriggerCommentCreatedCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class CommentTaskProgressOutboxHandler implements OutboxHandler {

    private final ObjectMapper objectMapper;
    private final TaskProgressApplicationService applicationService;
    private final String topic;

    public CommentTaskProgressOutboxHandler(
            ObjectMapper objectMapper,
            TaskProgressApplicationService applicationService,
            @Value("${growth.task.outbox.comment-topic:projection.growth.task.comment}") String topic
    ) {
        this.objectMapper = objectMapper;
        this.applicationService = applicationService;
        this.topic = topic;
    }

    @Override
    public String topic() {
        return topic;
    }

    @Override
    public void handle(OutboxEvent event) {
        if (event == null || !StringUtils.hasText(event.payload())) {
            return;
        }

        CommentPayload payload;
        try {
            payload = objectMapper.readValue(event.payload(), CommentPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException("growth task comment outbox payload 反序列化失败", e);
        }

        if (payload.getCommentId() == null || payload.getUserId() == null || payload.getCreateTime() == null) {
            return;
        }
        applicationService.triggerCommentCreated(new TriggerCommentCreatedCommand(
                payload.getCommentId(),
                payload.getUserId(),
                payload.getCreateTime()
        ));
    }
}
