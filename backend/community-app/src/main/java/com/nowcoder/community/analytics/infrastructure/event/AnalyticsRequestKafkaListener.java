package com.nowcoder.community.analytics.infrastructure.event;

import com.nowcoder.community.analytics.application.AnalyticsIngestApplicationService;
import com.nowcoder.community.analytics.application.command.RecordRequestCommand;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "analytics.ingest", name = {"enabled", "async-enabled"}, havingValue = "true")
public class AnalyticsRequestKafkaListener {

    private final AnalyticsIngestApplicationService analyticsIngestApplicationService;

    public AnalyticsRequestKafkaListener(AnalyticsIngestApplicationService analyticsIngestApplicationService) {
        this.analyticsIngestApplicationService = analyticsIngestApplicationService;
    }

    @KafkaListener(
            topics = "${analytics.ingest.kafka-topic:analytics.request}",
            groupId = "${analytics.ingest.kafka-group-id:analytics-request}",
            concurrency = "${analytics.ingest.kafka-concurrency:2}"
    )
    public void onMessage(AnalyticsRequestEvent event) {
        if (event == null) {
            return;
        }
        analyticsIngestApplicationService.recordRequest(new RecordRequestCommand(
                event.ip(),
                event.userId(),
                event.recordUv(),
                event.recordDau()
        ));
    }
}
