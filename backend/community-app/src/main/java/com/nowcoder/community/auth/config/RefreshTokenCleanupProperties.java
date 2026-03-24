package com.nowcoder.community.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.refresh.cleanup")
public class RefreshTokenCleanupProperties {

    private boolean enabled = true;

    private long intervalMs = 3600000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs > 0 ? intervalMs : 3600000L;
    }
}

