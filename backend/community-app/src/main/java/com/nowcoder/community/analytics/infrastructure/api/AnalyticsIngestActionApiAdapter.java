package com.nowcoder.community.analytics.infrastructure.api;

import com.nowcoder.community.analytics.api.action.AnalyticsIngestActionApi;
import com.nowcoder.community.analytics.application.AnalyticsIngestApplicationService;
import com.nowcoder.community.analytics.application.command.RecordLoginSuccessCommand;
import com.nowcoder.community.analytics.infrastructure.web.AnalyticsIngestProperties;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AnalyticsIngestActionApiAdapter implements AnalyticsIngestActionApi {

    private final AnalyticsIngestApplicationService analyticsIngestApplicationService;
    private final AnalyticsIngestProperties properties;

    public AnalyticsIngestActionApiAdapter(
            AnalyticsIngestApplicationService analyticsIngestApplicationService,
            AnalyticsIngestProperties properties
    ) {
        this.analyticsIngestApplicationService = analyticsIngestApplicationService;
        this.properties = properties;
    }

    @Override
    public void recordLoginSuccess(UUID userId) {
        analyticsIngestApplicationService.recordLoginSuccess(new RecordLoginSuccessCommand(
                userId,
                properties != null && properties.isEnabled() && properties.isRecordDau()
        ));
    }
}
