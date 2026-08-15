package com.nowcoder.community.analytics.infrastructure.api;

import com.nowcoder.community.analytics.api.action.AnalyticsIngestActionApi;
import com.nowcoder.community.analytics.application.AnalyticsIngestApplicationService;
import com.nowcoder.community.analytics.config.AnalyticsIngestProperties;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.Objects;

@Service
public class AnalyticsIngestActionApiAdapter implements AnalyticsIngestActionApi {

    private final AnalyticsIngestApplicationService analyticsIngestApplicationService;
    private final AnalyticsIngestProperties properties;

    public AnalyticsIngestActionApiAdapter(
            AnalyticsIngestApplicationService analyticsIngestApplicationService,
            AnalyticsIngestProperties properties
    ) {
        this.analyticsIngestApplicationService = Objects.requireNonNull(
                analyticsIngestApplicationService,
                "analyticsIngestApplicationService must not be null"
        );
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public void recordLoginSuccess(UUID userId) {
        analyticsIngestApplicationService.recordLoginSuccess(new AnalyticsIngestApplicationService.RecordLoginSuccess(
                userId,
                properties.isEnabled() && properties.isRecordDau()
        ));
    }
}
