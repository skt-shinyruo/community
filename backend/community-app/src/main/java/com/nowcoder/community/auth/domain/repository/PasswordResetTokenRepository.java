package com.nowcoder.community.auth.domain.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public interface PasswordResetTokenRepository {

    void store(String token, UUID userId, long securityVersionAtIssue, Duration ttl);

    PendingPasswordResetToken beginConfirmation(
            String token,
            Instant pendingExpiresAt,
            UUID confirmationLeaseId
    );

    boolean finishConfirmation(
            String token,
            UUID userId,
            long securityVersionAtIssue,
            UUID confirmationLeaseId
    );

    boolean rollbackConfirmation(
            String token,
            UUID userId,
            long securityVersionAtIssue,
            UUID confirmationLeaseId
    );

    void delete(String token);

    void revokeGeneration(UUID userId, long securityVersionAtIssue, Duration minimumTtl);

    record PendingPasswordResetToken(
            UUID userId,
            long securityVersionAtIssue,
            UUID confirmationLeaseId
    ) {
    }
}
