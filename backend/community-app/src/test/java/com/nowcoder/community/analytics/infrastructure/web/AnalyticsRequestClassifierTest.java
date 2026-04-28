package com.nowcoder.community.analytics.infrastructure.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsRequestClassifierTest {

    @Test
    void shouldCaptureIncludedSuccessfulApiRequest() {
        AnalyticsIngestProperties properties = new AnalyticsIngestProperties();
        properties.setEnabled(true);
        properties.setIncludePaths(List.of("/api/posts/**", "/api/search/**"));
        properties.setExcludePaths(List.of("/api/analytics/**", "/api/auth/**"));
        AnalyticsRequestClassifier classifier = new AnalyticsRequestClassifier(properties);

        AnalyticsRequestClassifier.Decision decision = classifier.classify("GET", "/api/posts/123", 200);

        assertThat(decision.capture()).isTrue();
        assertThat(decision.normalizedPath()).isEqualTo("/api/posts/{id}");
    }

    @Test
    void shouldSkipExcludedPathEvenWhenIncludedByApiPrefix() {
        AnalyticsIngestProperties properties = new AnalyticsIngestProperties();
        properties.setEnabled(true);
        properties.setIncludePaths(List.of("/api/**"));
        properties.setExcludePaths(List.of("/api/analytics/**"));
        AnalyticsRequestClassifier classifier = new AnalyticsRequestClassifier(properties);

        AnalyticsRequestClassifier.Decision decision = classifier.classify("GET", "/api/analytics/uv", 200);

        assertThat(decision.capture()).isFalse();
        assertThat(decision.reason()).isEqualTo("excluded_path");
    }

    @Test
    void shouldSkipWhenDisabledOrServerError() {
        AnalyticsIngestProperties properties = new AnalyticsIngestProperties();
        properties.setEnabled(false);
        AnalyticsRequestClassifier classifier = new AnalyticsRequestClassifier(properties);

        assertThat(classifier.classify("GET", "/api/posts/123", 200).capture()).isFalse();

        properties.setEnabled(true);
        properties.setIncludePaths(List.of("/api/posts/**"));
        assertThat(classifier.classify("GET", "/api/posts/123", 500).capture()).isFalse();
    }
}
