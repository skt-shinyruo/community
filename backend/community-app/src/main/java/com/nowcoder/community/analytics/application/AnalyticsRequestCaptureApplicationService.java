package com.nowcoder.community.analytics.application;

import com.nowcoder.community.analytics.application.command.RecordRequestCommand;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AnalyticsRequestCaptureApplicationService {

    private final AnalyticsIngestApplicationService analyticsIngestApplicationService;
    private final AnalyticsRequestCapturePort analyticsRequestCapturePort;

    @Autowired
    public AnalyticsRequestCaptureApplicationService(
            AnalyticsIngestApplicationService analyticsIngestApplicationService,
            ObjectProvider<AnalyticsRequestCapturePort> analyticsRequestCapturePortProvider
    ) {
        this(
                analyticsIngestApplicationService,
                analyticsRequestCapturePortProvider == null ? null : analyticsRequestCapturePortProvider.getIfAvailable()
        );
    }

    public AnalyticsRequestCaptureApplicationService(
            AnalyticsIngestApplicationService analyticsIngestApplicationService,
            AnalyticsRequestCapturePort analyticsRequestCapturePort
    ) {
        this.analyticsIngestApplicationService = analyticsIngestApplicationService;
        this.analyticsRequestCapturePort = analyticsRequestCapturePort;
    }

    public void capture(RecordRequestCommand command, boolean asyncEnabled) {
        Objects.requireNonNull(command, "command must not be null");
        if (asyncEnabled && analyticsRequestCapturePort != null) {
            analyticsRequestCapturePort.publish(command);
            return;
        }
        analyticsIngestApplicationService.recordRequest(command);
    }
}
