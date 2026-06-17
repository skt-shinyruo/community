package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.content.application.ContentEventDispatchApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${content.events.publisher:outbox-kafka}' == 'outbox-kafka' && '${events.outbox.enabled:true}' == 'true'")
public class ContentEventKafkaOutboxHandler implements OutboxHandler {

    private final ContentEventDispatchApplicationService applicationService;
    private final String outboxTopic;

    public ContentEventKafkaOutboxHandler(
            ContentEventDispatchApplicationService applicationService,
            @Value("${content.events.outbox-topic:eventbus.content}") String outboxTopic
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
