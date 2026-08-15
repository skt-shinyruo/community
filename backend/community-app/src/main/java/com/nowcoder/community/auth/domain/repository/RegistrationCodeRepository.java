package com.nowcoder.community.auth.domain.repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface RegistrationCodeRepository {

    IssueResult issue(UUID userId, String code, Duration ttl, Duration cooldown, UUID deliveryId);

    Optional<ReplacementLease> tryBeginReplacement(
            UUID userId,
            String code,
            Duration ttl,
            Duration cooldown,
            Duration leaseTtl
    );

    /**
     * Fences an outbox delivery against the currently active code (initial mail) or
     * the exact replacement lease (resend mail). The returned claim owns completion
     * and lease recovery after SMTP succeeds.
     */
    Optional<DeliveryClaim> claimMailDelivery(
            UUID userId,
            UUID deliveryId,
            String code,
            UUID replacementLeaseId,
            Duration leaseTtl,
            Duration minimumRemainingValidity
    );

    VerificationResult claimVerification(UUID userId, String code, Duration leaseTtl);

    void delete(UUID userId);

    enum IssueResult {
        ISSUED,
        COOLDOWN_ACTIVE
    }

    interface ReplacementLease {

        UUID id();

        boolean abort();
    }

    interface DeliveryClaim {

        boolean complete();
    }

    interface VerificationResult {
    }

    interface VerificationClaim extends VerificationResult {

        boolean consume();

        boolean restore();
    }

    enum VerificationFailure implements VerificationResult {
        NOT_FOUND,
        EXPIRED,
        MISMATCH,
        TOO_MANY_ATTEMPTS,
        PENDING_CONFLICT
    }
}
