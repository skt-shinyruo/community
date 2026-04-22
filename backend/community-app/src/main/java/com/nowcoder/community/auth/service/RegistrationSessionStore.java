package com.nowcoder.community.auth.service;

import java.time.Duration;
import java.util.UUID;

/**
 * Server-side mapping from opaque registrationToken -> userId for pending registrations.
 * <p>
 * This prevents anonymous endpoints (resend/verify) from being targetable by guessing sequential userId.
 */
public interface RegistrationSessionStore {

    /**
     * Issue a new opaque registration token and store the mapping with the given TTL.
     *
     * @param userId pending user id
     * @param ttl   TTL for the pending registration context
     * @return opaque registration token, or {@code null} if it cannot be issued
     */
    String issue(UUID userId, Duration ttl);

    /**
     * Resolve the token back to userId.
     *
     * @return userId, or {@code null} if missing/expired/invalid
     */
    UUID findUserId(String registrationToken);

    /**
     * Best-effort delete the mapping.
     */
    void delete(String registrationToken);
}
