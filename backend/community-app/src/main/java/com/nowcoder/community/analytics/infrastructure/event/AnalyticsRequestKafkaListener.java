package com.nowcoder.community.analytics.infrastructure.event;

import com.nowcoder.community.analytics.application.AnalyticsIngestApplicationService;
import com.nowcoder.community.analytics.application.command.RecordRequestCommand;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
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

    public record AnalyticsRequestEvent(
            String ip,
            UUID userId,
            boolean recordUv,
            boolean recordDau
    ) {
    }
}
