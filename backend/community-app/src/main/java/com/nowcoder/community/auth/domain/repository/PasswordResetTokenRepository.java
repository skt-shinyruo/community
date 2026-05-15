package com.nowcoder.community.auth.domain.repository;

import java.time.Duration;
import java.util.UUID;

public interface PasswordResetTokenRepository {

    void store(String token, UUID userId, Duration ttl);

    ConsumedPasswordResetToken consumeWithTtl(String token);

    void delete(String token);

    default UUID consume(String token) {
        ConsumedPasswordResetToken consumed = consumeWithTtl(token);
        return consumed == null ? null : consumed.userId();
    }

    record ConsumedPasswordResetToken(UUID userId, Duration remainingTtl) {
    }
}
