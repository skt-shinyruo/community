package com.nowcoder.yierloom.plugins.support;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

public final class SamplingDecider {
    private final double sampleRate;
    private final DoubleSupplier random;

    public SamplingDecider(double sampleRate) {
        this(sampleRate, () -> ThreadLocalRandom.current().nextDouble());
    }

    SamplingDecider(double sampleRate, DoubleSupplier random) {
        if (!Double.isFinite(sampleRate) || sampleRate < 0.0 || sampleRate > 1.0) {
            throw new IllegalArgumentException("sampleRate must be finite and between 0 and 1");
        }
        this.sampleRate = sampleRate;
        this.random = java.util.Objects.requireNonNull(random, "random");
    }

    public boolean sample() {
        if (sampleRate >= 1.0) {
            return true;
        }
        return sampleRate > 0.0 && random.getAsDouble() < sampleRate;
    }
}
