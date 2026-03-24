package com.nowcoder.community.auth.service;

import java.time.Duration;

public interface RegistrationCodeStore {

    void save(int userId, String code, Duration ttl);

    IssueResult issue(int userId, String code, Duration ttl, Duration cooldown);

    Long lastSentAtMillis(int userId);

    VerifyResult verifyAndConsume(int userId, String code);

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
