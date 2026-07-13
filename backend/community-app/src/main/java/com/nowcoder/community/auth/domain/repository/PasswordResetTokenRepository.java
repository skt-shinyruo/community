package com.nowcoder.community.auth.domain.repository;

import java.time.Duration;
import java.util.UUID;

public interface PasswordResetTokenRepository {

    void store(String token, UUID userId, Duration ttl);

    ConsumedPasswordResetToken consumeWithTtl(String token);

    void delete(String token);

    UUID consume(String token);

    record ConsumedPasswordResetToken(UUID userId, Duration remainingTtl) {
    }
}
