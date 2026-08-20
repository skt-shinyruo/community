package com.nowcoder.community.analytics.application;

import com.nowcoder.community.analytics.application.command.RecordRequestCommand;
import com.nowcoder.community.analytics.config.AnalyticsIngestProperties;
import com.nowcoder.community.common.spring.feature.FeatureFlagProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;

@Service
public class AnalyticsRequestCaptureApplicationService {

    private final AnalyticsIngestProperties properties;
    private final FeatureFlagProperties featureFlags;
    private final AnalyticsRequestCapturePort analyticsRequestCapturePort;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AnalyticsRequestCaptureApplicationService(
            AnalyticsIngestProperties properties,
            FeatureFlagProperties featureFlags,
            AnalyticsRequestCapturePort analyticsRequestCapturePort
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.featureFlags = Objects.requireNonNull(featureFlags, "featureFlags must not be null");
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
        if (!properties.isEnabled()
                || !Boolean.TRUE.equals(featureFlags.getFeatures().getOrDefault("analytics-ingest", true))) {
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
