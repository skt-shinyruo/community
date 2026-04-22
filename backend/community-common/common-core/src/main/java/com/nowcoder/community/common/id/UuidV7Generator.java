package com.nowcoder.community.common.id;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class UuidV7Generator {

    private static final long MAX_TIMESTAMP = 0xffff_ffff_ffffL;
    private static final int MAX_SEQUENCE = 0x0fff;
    private static final long RAND_B_MASK = 0x3fff_ffff_ffff_ffffL;
    private static final long RFC_4122_VARIANT = 0x8000_0000_0000_0000L;

    private final Clock clock;

    private long lastTimestamp = Long.MIN_VALUE;
    private int sequence;

    public UuidV7Generator() {
        this(Clock.systemUTC());
    }

    public UuidV7Generator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized UUID next() {
        long now = clock.millis();
        if (lastTimestamp == Long.MIN_VALUE || now > lastTimestamp) {
            lastTimestamp = now;
            sequence = 0;
        } else if (sequence < MAX_SEQUENCE) {
            sequence++;
        } else {
            lastTimestamp++;
            sequence = 0;
        }

        long timestamp = lastTimestamp & MAX_TIMESTAMP;
        long msb = (timestamp << 16) | 0x0000_0000_0000_7000L | (sequence & MAX_SEQUENCE);
        long lsb = RFC_4122_VARIANT | (ThreadLocalRandom.current().nextLong() & RAND_B_MASK);
        return new UUID(msb, lsb);
    }
}
