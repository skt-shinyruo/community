package com.nowcoder.community.auth.domain.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public interface RegistrationCodeRepository {

    IssueResult issue(UUID userId, String code, Duration ttl, Duration cooldown, UUID deliveryId);

    IssueResult beginReplacement(
            UUID userId,
            String code,
            Duration ttl,
            Duration cooldown,
            Instant leaseExpiresAt,
            UUID leaseId
    );

    boolean promoteReplacement(UUID userId, UUID leaseId);

    boolean promoteReplacement(UUID userId, UUID leaseId, Duration minimumRemainingValidity);

    boolean abortReplacement(UUID userId, UUID leaseId);

    /**
     * Fences an outbox delivery against the currently active code (initial mail) or
     * the exact replacement lease (resend mail). Replacement delivery also renews
     * its lease so an SMTP attempt cannot be displaced by another resend.
     */
    boolean prepareMailDelivery(
            UUID userId,
            UUID deliveryId,
            String code,
            UUID replacementLeaseId,
            Instant leaseExpiresAt
    );

    boolean prepareMailDelivery(
            UUID userId,
            UUID deliveryId,
            String code,
            UUID replacementLeaseId,
            Instant leaseExpiresAt,
            Duration minimumRemainingValidity
    );

    boolean completeInitialDelivery(
            UUID userId,
            UUID deliveryId,
            String code,
            Duration minimumRemainingValidity
    );

    void delete(UUID userId);

    VerifyResult verifyForConsumption(UUID userId, String code, Instant leaseExpiresAt, UUID leaseId);

    boolean consumePending(UUID userId, UUID leaseId);

    boolean restorePending(UUID userId, UUID leaseId);

    enum IssueResult {
        ISSUED,
        COOLDOWN_ACTIVE
    }

    enum VerifyResult {
        NOT_FOUND,
        EXPIRED,
        MISMATCH,
        TOO_MANY_ATTEMPTS,
        PENDING,
        PENDING_CONFLICT
    }
}
