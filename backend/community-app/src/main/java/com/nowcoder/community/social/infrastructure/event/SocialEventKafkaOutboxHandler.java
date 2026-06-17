package com.nowcoder.community.social.infrastructure.event;

import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.social.application.SocialEventDispatchApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${social.events.publisher:outbox-kafka}' == 'outbox-kafka' && '${events.outbox.enabled:true}' == 'true'")
public class SocialEventKafkaOutboxHandler implements OutboxHandler {

    private final SocialEventDispatchApplicationService applicationService;
    private final String outboxTopic;

    public SocialEventKafkaOutboxHandler(
            SocialEventDispatchApplicationService applicationService,
            @Value("${social.events.outbox-topic:eventbus.social}") String outboxTopic
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
