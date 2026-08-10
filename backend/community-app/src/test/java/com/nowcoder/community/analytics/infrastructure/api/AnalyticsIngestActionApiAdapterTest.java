package com.nowcoder.community.analytics.infrastructure.api;

import com.nowcoder.community.analytics.application.AnalyticsIngestApplicationService;
import com.nowcoder.community.analytics.infrastructure.web.AnalyticsIngestProperties;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AnalyticsIngestActionApiAdapterTest {

    @Test
    void enabledDauCaptureShouldReachTheOwnerApplicationEntry() {
        AnalyticsIngestApplicationService applicationService = mock(AnalyticsIngestApplicationService.class);
        AnalyticsIngestProperties properties = new AnalyticsIngestProperties();
        properties.setEnabled(true);
        properties.setRecordDau(true);
        AnalyticsIngestActionApiAdapter adapter = new AnalyticsIngestActionApiAdapter(applicationService, properties);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        adapter.recordLoginSuccess(userId);

        verify(applicationService).recordLoginSuccess(
                new AnalyticsIngestApplicationService.RecordLoginSuccess(userId, true));
    }
}
