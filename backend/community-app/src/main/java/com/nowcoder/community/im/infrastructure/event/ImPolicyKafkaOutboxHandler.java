package com.nowcoder.community.im.infrastructure.event;

import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.im.application.ImPolicyEventDispatchApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class ImPolicyKafkaOutboxHandler implements OutboxHandler {

    private final ImPolicyEventDispatchApplicationService applicationService;
    private final String outboxTopic;

    public ImPolicyKafkaOutboxHandler(
            ImPolicyEventDispatchApplicationService applicationService,
            @Value("${im.policy.outbox.topic:projection.im.policy}") String outboxTopic
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
        if (event == null) {
            return;
        }
        applicationService.dispatch(event.eventId(), event.eventKey(), event.payload());
    }
}
