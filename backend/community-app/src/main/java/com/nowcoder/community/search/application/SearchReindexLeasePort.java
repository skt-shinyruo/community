package com.nowcoder.community.search.application;

import java.time.Duration;
import java.util.Optional;

/**
 * Provides cluster-wide single-flight protection for a full search rebuild.
 */
public interface SearchReindexLeasePort {

    Optional<Lease> tryAcquire(Duration ttl);

    interface Lease extends AutoCloseable {

        boolean isValid();

        @Override
        void close();
    }
}
