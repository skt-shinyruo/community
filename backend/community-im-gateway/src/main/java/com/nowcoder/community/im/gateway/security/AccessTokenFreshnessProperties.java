package com.nowcoder.community.im.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "im.access-token-freshness")
public class AccessTokenFreshnessProperties {

    private String communityServiceId = "community-app";
    private Duration requestTimeout = Duration.ofMillis(500);

    public String getCommunityServiceId() {
        return communityServiceId;
    }

    public void setCommunityServiceId(String communityServiceId) {
        this.communityServiceId = communityServiceId;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    String normalizedCommunityServiceId() {
        return communityServiceId == null || communityServiceId.isBlank()
                ? "community-app"
                : communityServiceId.trim();
    }

    Duration normalizedRequestTimeout() {
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            return Duration.ofMillis(500);
        }
        return requestTimeout;
    }
}
