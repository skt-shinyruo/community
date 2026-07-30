package com.nowcoder.yierloom.plugins.support;

import java.util.Objects;
import java.util.function.LongSupplier;

public final class TokenBucketRateLimiter {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final int capacity;
    private final LongSupplier nanoTime;
    private int tokens;
    private long lastRefillNanos;

    public TokenBucketRateLimiter(int maxEventsPerSecond) {
        this(maxEventsPerSecond, System::nanoTime);
    }

    TokenBucketRateLimiter(int maxEventsPerSecond, LongSupplier nanoTime) {
        if (maxEventsPerSecond < 0) {
            throw new IllegalArgumentException("maxEventsPerSecond must not be negative");
        }
        this.capacity = maxEventsPerSecond;
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.tokens = capacity;
        this.lastRefillNanos = this.nanoTime.getAsLong();
    }

    public synchronized boolean tryAcquire() {
        if (capacity == 0) {
            return false;
        }
        refill();
        if (tokens == 0) {
            return false;
        }
        tokens--;
        return true;
    }

    private void refill() {
        long now = nanoTime.getAsLong();
        if (now - lastRefillNanos >= NANOS_PER_SECOND) {
            lastRefillNanos = now;
            tokens = capacity;
        }
    }
}
