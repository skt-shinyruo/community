package com.nowcoder.community.gateway.edge;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "gateway.http.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private boolean failOpenOnError = true;
    private final Map<String, Policy> policies = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFailOpenOnError() {
        return failOpenOnError;
    }

    public void setFailOpenOnError(boolean failOpenOnError) {
        this.failOpenOnError = failOpenOnError;
    }

    public Map<String, Policy> getPolicies() {
        return policies;
    }

    public static class Policy {

        private boolean enabled = true;
        private int limit = 100;
        private Duration window = Duration.ofMinutes(1);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }
}
