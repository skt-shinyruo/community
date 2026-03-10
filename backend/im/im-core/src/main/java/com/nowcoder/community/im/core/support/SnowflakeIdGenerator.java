package com.nowcoder.community.im.core.support;

import java.time.Clock;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A simple Snowflake-like 64-bit id generator:
 *
 * <pre>
 *  41 bits: timestamp (ms) since epoch
 *  10 bits: node id
 *  12 bits: sequence per ms
 * </pre>
 *
 * This is not a strict distributed coordination scheme; it assumes unique node id per process.
 */
public final class SnowflakeIdGenerator implements IdGenerator {

    private static final long NODE_ID_BITS = 10L;
    private static final long SEQ_BITS = 12L;

    private static final long MAX_NODE_ID = (1L << NODE_ID_BITS) - 1L;
    private static final long MAX_SEQ = (1L << SEQ_BITS) - 1L;

    private static final long NODE_ID_SHIFT = SEQ_BITS;
    private static final long TS_SHIFT = NODE_ID_BITS + SEQ_BITS;

    private final Clock clock;
    private final long nodeId;

    private final AtomicLong lastTsAndSeq = new AtomicLong(0L);

    public SnowflakeIdGenerator(Clock clock, long nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException("nodeId must be between 0 and " + MAX_NODE_ID);
        }
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.nodeId = nodeId;
    }

    @Override
    public long nextId() {
        while (true) {
            long now = clock.millis();
            long prev = lastTsAndSeq.get();
            long prevTs = prev >>> SEQ_BITS;
            long prevSeq = prev & MAX_SEQ;

            long nextSeq;
            long ts;
            if (now > prevTs) {
                ts = now;
                nextSeq = ThreadLocalRandom.current().nextLong(0, 8);
            } else if (now == prevTs) {
                ts = prevTs;
                nextSeq = prevSeq + 1;
                if (nextSeq > MAX_SEQ) {
                    ts = waitNextMillis(prevTs);
                    nextSeq = 0;
                }
            } else {
                ts = prevTs;
                nextSeq = prevSeq + 1;
                if (nextSeq > MAX_SEQ) {
                    ts = waitNextMillis(prevTs);
                    nextSeq = 0;
                }
            }

            long next = (ts << SEQ_BITS) | (nextSeq & MAX_SEQ);
            if (lastTsAndSeq.compareAndSet(prev, next)) {
                return (ts << TS_SHIFT) | (nodeId << NODE_ID_SHIFT) | nextSeq;
            }
        }
    }

    private long waitNextMillis(long lastTs) {
        long ts = clock.millis();
        while (ts <= lastTs) {
            ts = clock.millis();
        }
        return ts;
    }
}

