package com.nowcoder.community.common.spring.policy;

import java.time.Duration;

public class CachePolicyDecisions {

    private final CachePolicyProperties properties;

    public CachePolicyDecisions(CachePolicyProperties properties) {
        this.properties = properties == null ? new CachePolicyProperties() : properties;
    }

    public Duration defaultTtl() {
        return properties.getDefaultTtl();
    }

    public Duration nullTtl() {
        return properties.getNullTtl();
    }

    public boolean isHotspot(long count) {
        return count >= properties.getHotspotThreshold();
    }

    public boolean prewarmEnabled() {
        return properties.isPrewarmEnabled();
    }

    public boolean diagnosticBypassEnabled() {
        return properties.isDiagnosticBypassEnabled();
    }
}
