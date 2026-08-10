package com.nowcoder.community.analytics.infrastructure.web;

import com.nowcoder.community.common.spring.feature.FeatureFlagDecisions;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Component
public class AnalyticsRequestClassifier {

    private final AnalyticsIngestProperties properties;
    private final FeatureFlagDecisions featureFlags;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AnalyticsRequestClassifier(AnalyticsIngestProperties properties, FeatureFlagDecisions featureFlags) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.featureFlags = Objects.requireNonNull(featureFlags, "featureFlags must not be null");
    }

    public Decision classify(String method, String path, int status) {
        if (!properties.isEnabled()) {
            return Decision.skip("disabled");
        }
        if (!featureFlags.enabledOrDefault("analytics-ingest", true)) {
            return Decision.skip("feature_disabled");
        }
        if (!StringUtils.hasText(method) || !StringUtils.hasText(path)) {
            return Decision.skip("missing_request");
        }
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return Decision.skip("options");
        }
        if (status >= 500) {
            return Decision.skip("server_error");
        }
        if (matchesAny(path, properties.getExcludePaths())) {
            return Decision.skip("excluded_path");
        }
        if (!matchesAny(path, properties.getIncludePaths())) {
            return Decision.skip("not_included");
        }
        return new Decision(true, normalizePath(path), "captured");
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

    private String normalizePath(String path) {
        return path.replaceAll("/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?=/|$)", "/{uuid}")
                .replaceAll("/\\d+(?=/|$)", "/{id}");
    }

    public record Decision(boolean capture, String normalizedPath, String reason) {
        static Decision skip(String reason) {
            return new Decision(false, null, reason);
        }
    }
}
