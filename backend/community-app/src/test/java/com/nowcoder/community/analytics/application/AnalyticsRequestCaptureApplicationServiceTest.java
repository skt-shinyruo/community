package com.nowcoder.community.analytics.application;

import com.nowcoder.community.analytics.application.AnalyticsRequestCaptureApplicationService.RequestObservation;
import com.nowcoder.community.analytics.application.command.RecordRequestCommand;
import com.nowcoder.community.analytics.config.AnalyticsIngestProperties;
import com.nowcoder.community.common.spring.feature.FeatureFlagDecisions;
import com.nowcoder.community.common.spring.feature.FeatureFlagProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AnalyticsRequestCaptureApplicationServiceTest {

    @Test
    void shouldClassifyObservationAndPublishConfiguredMetricPolicy() {
        AnalyticsIngestProperties properties = enabledProperties();
        properties.setRecordUv(false);
        properties.setRecordDau(true);
        AnalyticsRequestCapturePort capturePort = mock(AnalyticsRequestCapturePort.class);
        AnalyticsRequestCaptureApplicationService service = service(properties, defaultFeatureFlags(), capturePort);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        service.capture(new RequestObservation(
                "GET",
                "/api/posts/123",
                200,
                "1.1.1.1",
                userId
        ));

        verify(capturePort).publish(new RecordRequestCommand("1.1.1.1", userId, false, true));
    }

    @Test
    void shouldSkipObservationRejectedByRequestPolicy() {
        AnalyticsIngestProperties properties = enabledProperties();
        properties.setIncludePaths(List.of("/api/**"));
        properties.setExcludePaths(List.of("/api/analytics/**"));
        AnalyticsRequestCapturePort capturePort = mock(AnalyticsRequestCapturePort.class);
        AnalyticsRequestCaptureApplicationService service = service(properties, defaultFeatureFlags(), capturePort);

        service.capture(new RequestObservation("GET", "/api/analytics/uv", 200, "1.1.1.1", null));
        service.capture(new RequestObservation("OPTIONS", "/api/posts/123", 200, "1.1.1.1", null));
        service.capture(new RequestObservation("GET", "/api/posts/123", 500, "1.1.1.1", null));
        service.capture(new RequestObservation("GET", "/outside", 200, "1.1.1.1", null));
        service.capture(new RequestObservation(null, "/api/posts/123", 200, "1.1.1.1", null));

        verifyNoInteractions(capturePort);
    }

    @Test
    void shouldSkipWhenCaptureConfigurationIsDisabled() {
        AnalyticsIngestProperties properties = enabledProperties();
        properties.setEnabled(false);
        AnalyticsRequestCapturePort capturePort = mock(AnalyticsRequestCapturePort.class);
        AnalyticsRequestCaptureApplicationService service = service(properties, defaultFeatureFlags(), capturePort);

        service.capture(new RequestObservation("GET", "/api/posts/123", 200, "1.1.1.1", null));

        verifyNoInteractions(capturePort);
    }

    @Test
    void shouldSkipWhenDynamicFeatureFlagIsDisabled() {
        AnalyticsIngestProperties properties = enabledProperties();
        FeatureFlagProperties featureFlagProperties = new FeatureFlagProperties();
        featureFlagProperties.getFlags().put("analytics-ingest", false);
        AnalyticsRequestCapturePort capturePort = mock(AnalyticsRequestCapturePort.class);
        AnalyticsRequestCaptureApplicationService service = service(
                properties,
                new FeatureFlagDecisions(featureFlagProperties),
                capturePort
        );

        service.capture(new RequestObservation("GET", "/api/posts/123", 200, "1.1.1.1", null));

        verifyNoInteractions(capturePort);
    }

    private static AnalyticsRequestCaptureApplicationService service(
            AnalyticsIngestProperties properties,
            FeatureFlagDecisions featureFlags,
            AnalyticsRequestCapturePort capturePort
    ) {
        return new AnalyticsRequestCaptureApplicationService(properties, featureFlags, capturePort);
    }

    private static AnalyticsIngestProperties enabledProperties() {
        AnalyticsIngestProperties properties = new AnalyticsIngestProperties();
        properties.setEnabled(true);
        properties.setIncludePaths(List.of("/api/posts/**"));
        return properties;
    }

    private static FeatureFlagDecisions defaultFeatureFlags() {
        return new FeatureFlagDecisions(new FeatureFlagProperties());
    }
}
