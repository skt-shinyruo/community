package com.nowcoder.community.user.infrastructure.event;

import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.user.application.UserEventDispatchApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${user.events.publisher:outbox-kafka}' == 'outbox-kafka' && '${events.outbox.enabled:true}' == 'true'")
public class UserEventKafkaOutboxHandler implements OutboxHandler {

    private final UserEventDispatchApplicationService applicationService;
    private final String outboxTopic;

    public UserEventKafkaOutboxHandler(
            UserEventDispatchApplicationService applicationService,
            @Value("${user.events.outbox-topic:eventbus.user}") String outboxTopic
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
        applicationService.dispatch(event.eventKey(), event.payload());
    }
}
