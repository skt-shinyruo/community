package com.nowcoder.community.auth.domain.repository;

import java.time.Duration;
import java.util.UUID;

public interface RegistrationCodeRepository {

    void save(UUID userId, String code, Duration ttl);

    IssueResult issue(UUID userId, String code, Duration ttl, Duration cooldown);

    IssueResult beginReplacement(UUID userId, String code, Duration ttl, Duration cooldown);

    void promoteReplacement(UUID userId);

    void abortReplacement(UUID userId);

    Long lastSentAtMillis(UUID userId);

    void delete(UUID userId);

    VerifyResult verifyAndConsume(UUID userId, String code);

    VerifyResult verifyForConsumption(UUID userId, String code, Duration pendingTtl);

    void consumePending(UUID userId);

    void restorePending(UUID userId);

    enum IssueResult {
        ISSUED,
        COOLDOWN_ACTIVE
    }

    enum VerifyResult {
        SUCCESS,
        NOT_FOUND,
        EXPIRED,
        MISMATCH,
        TOO_MANY_ATTEMPTS,
        PENDING,
        PENDING_CONFLICT
    }
}
