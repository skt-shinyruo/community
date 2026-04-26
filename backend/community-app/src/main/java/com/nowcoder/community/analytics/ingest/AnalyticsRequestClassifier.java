package com.nowcoder.community.analytics.ingest;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

@Component
public class AnalyticsRequestClassifier {

    private final AnalyticsIngestProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AnalyticsRequestClassifier(AnalyticsIngestProperties properties) {
        this.properties = properties;
    }

    public Decision classify(String method, String path, int status) {
        if (properties == null || !properties.isEnabled()) {
            return Decision.skip("disabled");
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
