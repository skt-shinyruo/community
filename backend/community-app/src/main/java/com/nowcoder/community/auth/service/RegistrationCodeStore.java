package com.nowcoder.community.auth.service;

import java.time.Duration;
import java.util.UUID;

public interface RegistrationCodeStore {

    void save(UUID userId, String code, Duration ttl);

    IssueResult issue(UUID userId, String code, Duration ttl, Duration cooldown);

    Long lastSentAtMillis(UUID userId);

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
