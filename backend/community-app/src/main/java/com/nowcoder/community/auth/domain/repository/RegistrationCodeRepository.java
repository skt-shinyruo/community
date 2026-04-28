package com.nowcoder.community.auth.domain.repository;

import java.time.Duration;
import java.util.UUID;

public interface RegistrationCodeRepository {

    void save(UUID userId, String code, Duration ttl);

    IssueResult issue(UUID userId, String code, Duration ttl, Duration cooldown);

    Long lastSentAtMillis(UUID userId);

    void delete(UUID userId);

    VerifyResult verifyAndConsume(UUID userId, String code);

    enum IssueResult {
        ISSUED,
        COOLDOWN_ACTIVE
    }

    enum VerifyResult {
        SUCCESS,
        NOT_FOUND,
        EXPIRED,
        MISMATCH,
        TOO_MANY_ATTEMPTS
    }
}
