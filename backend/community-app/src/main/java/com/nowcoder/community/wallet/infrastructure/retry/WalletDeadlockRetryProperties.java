package com.nowcoder.community.wallet.infrastructure.retry;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "wallet.deadlock-retry")
public class WalletDeadlockRetryProperties {

    private int maxAttempts = 3;
    private Duration backoff = Duration.ofMillis(10);

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getBackoff() {
        return backoff;
    }

    public void setBackoff(Duration backoff) {
        this.backoff = backoff;
    }

    int normalizedMaxAttempts() {
        return Math.min(5, Math.max(1, maxAttempts));
    }

    Duration normalizedBackoff() {
        if (backoff == null || backoff.isNegative()) {
            return Duration.ZERO;
        }
        return backoff.compareTo(Duration.ofSeconds(1)) > 0 ? Duration.ofSeconds(1) : backoff;
    }
}
