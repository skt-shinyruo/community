package com.nowcoder.community.auth.domain.repository;

import java.util.UUID;

public interface LoginRateLimitRepository {

    int count(String key);

    /** Returns committed failures plus all unexpired password-check reservations. */
    int countBudget(String failureKey, String leaseKey);

    int increment(String key, int windowSeconds);

    void delete(String key);

    /**
     * Atomically reserves one password-check budget unit after accounting for
     * both committed failures and currently leased checks. Implementations
     * should keep both keys in the same Redis Cluster slot.
     */
    boolean tryAcquire(
            String failureKey,
            String leaseKey,
            UUID token,
            int limit,
            int leaseMillis
    );

    /** Renews a live in-flight slot only while the caller still owns it. */
    boolean renew(String key, UUID token, int leaseMillis);

    /** Releases one previously acquired in-flight slot. */
    void release(String key, UUID token);
}
