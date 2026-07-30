package com.nowcoder.yierloom.plugins.support;

import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TokenBucketRateLimiterTest {

    @Test
    void startsWithOneFullBurstAndRefillsFullyAfterOneSecond() {
        AtomicLong time = new AtomicLong();
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, time::get);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();

        time.set(999_999_999L);
        assertThat(limiter.tryAcquire()).isFalse();

        time.set(1_000_000_000L);
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void elapsedSecondsDoNotAccumulateAnUnboundedBurst() {
        AtomicLong time = new AtomicLong();
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, time::get);

        assertThat(limiter.tryAcquire()).isTrue();
        time.set(10_000_000_000L);
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void zeroDisablesAcquisitionAndNegativeCapacityIsRejected() {
        assertThat(new TokenBucketRateLimiter(0).tryAcquire()).isFalse();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TokenBucketRateLimiter(-1));
    }

    @Test
    void concurrentCallersCannotExceedCapacityAtARefillBoundary() {
        int capacity = 10;
        AtomicLong time = new AtomicLong();
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(capacity, time::get);
        IntStream.range(0, capacity / 2).forEach(ignored ->
                assertThat(limiter.tryAcquire()).isTrue());
        time.set(1_000_000_000L);

        long acquired = IntStream.range(0, 1_000)
                .parallel()
                .filter(ignored -> limiter.tryAcquire())
                .count();

        assertThat(acquired).isEqualTo(capacity);
    }
}
