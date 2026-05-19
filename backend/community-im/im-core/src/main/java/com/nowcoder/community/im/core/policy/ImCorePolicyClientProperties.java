package com.nowcoder.community.im.core.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "im.policy")
public class ImCorePolicyClientProperties {

    private String communityServiceId = "community-app";
    private String internalScope = "im.realtime.internal";
    private Duration requestTimeout = Duration.ofMillis(500);
    private Duration rejectionCacheTtl = Duration.ofMillis(500);
    private int rejectionCacheMaxEntries = 100_000;

    public String getCommunityServiceId() {
        return communityServiceId;
    }

    public void setCommunityServiceId(String communityServiceId) {
        this.communityServiceId = communityServiceId;
    }

    public String getInternalScope() {
        return internalScope;
    }

    public void setInternalScope(String internalScope) {
        this.internalScope = internalScope;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public Duration getRejectionCacheTtl() {
        return rejectionCacheTtl;
    }

    public void setRejectionCacheTtl(Duration rejectionCacheTtl) {
        this.rejectionCacheTtl = rejectionCacheTtl;
    }

    public int getRejectionCacheMaxEntries() {
        return rejectionCacheMaxEntries;
    }

    public void setRejectionCacheMaxEntries(int rejectionCacheMaxEntries) {
        this.rejectionCacheMaxEntries = rejectionCacheMaxEntries;
    }

    Duration normalizedRequestTimeout() {
        return normalize(requestTimeout, Duration.ofMillis(500), Duration.ofMillis(50));
    }

    Duration normalizedRejectionCacheTtl() {
        return normalize(rejectionCacheTtl, Duration.ofMillis(500), Duration.ZERO);
    }

    int normalizedRejectionCacheMaxEntries() {
        return Math.max(1, rejectionCacheMaxEntries);
    }

    private Duration normalize(Duration value, Duration fallback, Duration minimum) {
        Duration resolved = value == null ? fallback : value;
        if (resolved.compareTo(minimum) < 0) {
            return minimum;
        }
        return resolved;
    }
}
