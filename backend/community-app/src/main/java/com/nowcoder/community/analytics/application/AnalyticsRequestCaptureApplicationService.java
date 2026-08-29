package com.nowcoder.community.analytics.application;

import com.nowcoder.community.analytics.application.command.RecordRequestCommand;
import com.nowcoder.community.analytics.config.AnalyticsIngestProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;

@Service
public class AnalyticsRequestCaptureApplicationService {

    private final AnalyticsIngestProperties properties;
    private final boolean analyticsIngestEnabled;
    private final AnalyticsRequestCapturePort analyticsRequestCapturePort;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AnalyticsRequestCaptureApplicationService(
            AnalyticsIngestProperties properties,
            @Value("${community.features.analytics-ingest:true}") boolean analyticsIngestEnabled,
            AnalyticsRequestCapturePort analyticsRequestCapturePort
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.analyticsIngestEnabled = analyticsIngestEnabled;
        this.analyticsRequestCapturePort = Objects.requireNonNull(
                analyticsRequestCapturePort,
                "analyticsRequestCapturePort must not be null"
        );
    }

    public void capture(RequestObservation observation) {
        Objects.requireNonNull(observation, "observation must not be null");
        if (!shouldCapture(observation)) {
            return;
        }
        analyticsRequestCapturePort.publish(new RecordRequestCommand(
                observation.ip(),
                observation.userId(),
                properties.isRecordUv(),
                properties.isRecordDau()
        ));
    }

    private boolean shouldCapture(RequestObservation observation) {
        if (!properties.isEnabled() || !analyticsIngestEnabled) {
            return false;
        }
        if (!StringUtils.hasText(observation.method()) || !StringUtils.hasText(observation.path())) {
            return false;
        }
        if ("OPTIONS".equalsIgnoreCase(observation.method()) || observation.status() >= 500) {
            return false;
        }
        if (matchesAny(observation.path(), properties.getExcludePaths())) {
            return false;
        }
        return matchesAny(observation.path(), properties.getIncludePaths());
    }

    private boolean matchesAny(String path, Iterable<String> patterns) {
        if (patterns == null) {
            return false;
        }
        for (String pattern : patterns) {
            if (StringUtils.hasText(pattern) && pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    public record RequestObservation(
            String method,
            String path,
            int status,
            String ip,
            UUID userId
    ) {
    }
}
