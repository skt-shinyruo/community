package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.content.application.SocialInteractionProjectionApplicationService;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SocialInteractionBackboneKafkaListener {

    private final SocialInteractionProjectionApplicationService applicationService;

    public SocialInteractionBackboneKafkaListener(
            SocialInteractionProjectionApplicationService applicationService
    ) {
        this.applicationService = applicationService;
    }

    @KafkaListener(
            topics = "${social.events.kafka-topic:social.events}",
            groupId = "${content.score.kafka.consumer.group-id:content-post-score}",
            concurrency = "${content.score.kafka.consumer.concurrency:3}"
    )
    public void onSocialEvent(SocialContractEvent event) {
        applicationService.projectSocialEvent(event);
    }
}
