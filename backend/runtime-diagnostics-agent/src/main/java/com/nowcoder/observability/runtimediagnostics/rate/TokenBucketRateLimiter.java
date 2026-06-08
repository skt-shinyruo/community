package com.nowcoder.observability.runtimediagnostics.rate;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public class TokenBucketRateLimiter {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final int maxEventsPerSecond;
    private final LongSupplier nanoTime;
    private final AtomicInteger tokens;
    private final AtomicLong lastRefillNanos;

    public TokenBucketRateLimiter(int maxEventsPerSecond, LongSupplier nanoTime) {
        this.maxEventsPerSecond = Math.max(0, maxEventsPerSecond);
        this.nanoTime = nanoTime == null ? System::nanoTime : nanoTime;
        this.tokens = new AtomicInteger(this.maxEventsPerSecond);
        this.lastRefillNanos = new AtomicLong(this.nanoTime.getAsLong());
    }

    public boolean tryAcquire() {
        if (maxEventsPerSecond <= 0) {
            return false;
        }
        refill();
        while (true) {
            int current = tokens.get();
            if (current <= 0) {
                return false;
            }
            if (tokens.compareAndSet(current, current - 1)) {
                return true;
            }
        }
    }

    private void refill() {
        long now = nanoTime.getAsLong();
        long last = lastRefillNanos.get();
        if (now - last < NANOS_PER_SECOND) {
            return;
        }
        if (lastRefillNanos.compareAndSet(last, now)) {
            tokens.set(maxEventsPerSecond);
        }
    }
}
