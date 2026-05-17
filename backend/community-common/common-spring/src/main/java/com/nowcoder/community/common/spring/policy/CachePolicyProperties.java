package com.nowcoder.community.common.spring.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "community.cache")
public class CachePolicyProperties {

    private Duration nullTtl = Duration.ofSeconds(30);
    private Duration defaultTtl = Duration.ofSeconds(300);
    private int hotspotThreshold = 1000;
    private boolean prewarmEnabled = false;
    private boolean diagnosticBypassEnabled = false;

    public Duration getNullTtl() {
        return nullTtl;
    }

    public void setNullTtl(Duration nullTtl) {
        this.nullTtl = positiveOrDefault(nullTtl, Duration.ofSeconds(30));
    }

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = positiveOrDefault(defaultTtl, Duration.ofSeconds(300));
    }

    public int getHotspotThreshold() {
        return hotspotThreshold;
    }

    public void setHotspotThreshold(int hotspotThreshold) {
        this.hotspotThreshold = Math.max(0, hotspotThreshold);
    }

    public boolean isPrewarmEnabled() {
        return prewarmEnabled;
    }

    public void setPrewarmEnabled(boolean prewarmEnabled) {
        this.prewarmEnabled = prewarmEnabled;
    }

    public boolean isDiagnosticBypassEnabled() {
        return diagnosticBypassEnabled;
    }

    public void setDiagnosticBypassEnabled(boolean diagnosticBypassEnabled) {
        this.diagnosticBypassEnabled = diagnosticBypassEnabled;
    }

    private Duration positiveOrDefault(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }
}
