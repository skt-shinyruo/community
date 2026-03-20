package com.nowcoder.community.gateway.edge;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryRateLimiter {

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    public boolean allow(String key, RateLimitProperties.Policy policy) {
        if (policy == null) {
            return true;
        }
        int limit = Math.max(1, policy.getLimit());
        Duration window = policy.getWindow() == null ? Duration.ofMinutes(1) : policy.getWindow();
        long windowMillis = Math.max(1L, window.toMillis());
        long now = System.currentTimeMillis();

        Counter counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || now >= existing.windowEndEpochMillis) {
                return new Counter(now + windowMillis);
            }
            return existing;
        });
        return counter.incrementAndGet() <= limit;
    }

    private static final class Counter {

        private final long windowEndEpochMillis;
        private final AtomicInteger value = new AtomicInteger(0);

        private Counter(long windowEndEpochMillis) {
            this.windowEndEpochMillis = windowEndEpochMillis;
        }

        private int incrementAndGet() {
            return value.incrementAndGet();
        }
    }
}
