package com.nowcoder.community.growth.infrastructure.event;

import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.growth.application.TaskProgressOutboxDispatchApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class CommentTaskProgressKafkaOutboxHandler implements OutboxHandler {

    private final TaskProgressOutboxDispatchApplicationService applicationService;
    private final String outboxTopic;

    public CommentTaskProgressKafkaOutboxHandler(
            TaskProgressOutboxDispatchApplicationService applicationService,
            @Value("${growth.task.outbox.comment-topic:projection.growth.task.comment}") String outboxTopic
    ) {
        this.applicationService = applicationService;
        this.outboxTopic = outboxTopic;
    }

    @Override
    public String topic() {
        return outboxTopic;
    }

    @Override
    public void handle(OutboxEvent event) {
        applicationService.dispatchCommentCreated(event == null ? null : event.eventKey(), event == null ? null : event.payload());
    }
}
