package com.nowcoder.community.growth.infrastructure.event;

import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.growth.application.TaskProgressOutboxDispatchApplicationService;
import com.nowcoder.community.growth.application.command.DispatchTaskProgressEventCommand;
import com.nowcoder.community.growth.application.command.TaskProgressDispatchKind;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class LikeTaskProgressKafkaOutboxHandler implements OutboxHandler {

    private final TaskProgressOutboxDispatchApplicationService applicationService;
    private final String outboxTopic;

    public LikeTaskProgressKafkaOutboxHandler(
            TaskProgressOutboxDispatchApplicationService applicationService,
            @Value("${growth.task.outbox.like-topic:projection.growth.task.like}") String outboxTopic
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
        String eventId = event == null ? null : event.eventId();
        if (eventId != null && eventId.startsWith("like-removed:")) {
            applicationService.dispatch(new DispatchTaskProgressEventCommand(
                    TaskProgressDispatchKind.LIKE_REMOVED,
                    event.eventKey(),
                    event.payload()
            ));
            return;
        }
        applicationService.dispatch(new DispatchTaskProgressEventCommand(
                TaskProgressDispatchKind.LIKE_CREATED,
                event == null ? null : event.eventKey(),
                event == null ? null : event.payload()
        ));
    }
}
