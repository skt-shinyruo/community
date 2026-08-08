package com.nowcoder.community.market.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

final class MarketWalletActionRetryPolicy {

    static final int DEFAULT_MAX_RETRY_ATTEMPTS = 8;

    private MarketWalletActionRetryPolicy() {
    }

    static int normalizeMaxRetryAttempts(int maxRetryAttempts) {
        return maxRetryAttempts <= 0 ? DEFAULT_MAX_RETRY_ATTEMPTS : maxRetryAttempts;
    }

    static Instant nextRetryAt(Instant now, int retryCount) {
        long delaySeconds = Math.min(300L, 5L * (1L << Math.min(Math.max(retryCount, 0), 6)));
        return now.plus(delaySeconds, ChronoUnit.SECONDS);
    }
}
