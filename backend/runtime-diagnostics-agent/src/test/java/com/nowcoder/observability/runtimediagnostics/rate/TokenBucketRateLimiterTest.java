package com.nowcoder.observability.runtimediagnostics.rate;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketRateLimiterTest {

    @Test
    void allowsConfiguredEventsPerSecondAndRefills() {
        AtomicLong now = new AtomicLong(0);
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, now::get);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();

        now.set(1_000_000_000L);
        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    void zeroLimitDisablesEvents() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(0, System::nanoTime);

        assertThat(limiter.tryAcquire()).isFalse();
    }
}
