package com.nowcoder.community.analytics.application;

import com.nowcoder.community.analytics.application.command.RecordRequestCommand;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class AnalyticsRequestCaptureApplicationService {

    private final AnalyticsIngestApplicationService analyticsIngestApplicationService;
    private final Optional<AnalyticsRequestCapturePort> analyticsRequestCapturePort;

    public AnalyticsRequestCaptureApplicationService(
            AnalyticsIngestApplicationService analyticsIngestApplicationService,
            Optional<AnalyticsRequestCapturePort> analyticsRequestCapturePort
    ) {
        this.analyticsIngestApplicationService = Objects.requireNonNull(
                analyticsIngestApplicationService,
                "analyticsIngestApplicationService must not be null"
        );
        this.analyticsRequestCapturePort = Objects.requireNonNull(
                analyticsRequestCapturePort,
                "analyticsRequestCapturePort must not be null"
        );
    }

    public void capture(RecordRequestCommand command, boolean asyncEnabled) {
        Objects.requireNonNull(command, "command must not be null");
        if (asyncEnabled && analyticsRequestCapturePort.isPresent()) {
            analyticsRequestCapturePort.orElseThrow().publish(command);
            return;
        }
        analyticsIngestApplicationService.recordRequest(command);
    }
}
