package com.nowcoder.community.analytics.application;

import com.nowcoder.community.analytics.application.command.RecordRequestCommand;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AnalyticsRequestCaptureApplicationService {

    private final AnalyticsRequestCapturePort analyticsRequestCapturePort;

    public AnalyticsRequestCaptureApplicationService(AnalyticsRequestCapturePort analyticsRequestCapturePort) {
        this.analyticsRequestCapturePort = Objects.requireNonNull(
                analyticsRequestCapturePort,
                "analyticsRequestCapturePort must not be null"
        );
    }

    public void capture(RecordRequestCommand command) {
        analyticsRequestCapturePort.publish(Objects.requireNonNull(command, "command must not be null"));
    }
}
