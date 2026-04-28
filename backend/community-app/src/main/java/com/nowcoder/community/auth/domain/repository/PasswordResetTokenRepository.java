package com.nowcoder.community.auth.domain.repository;

import java.time.Duration;
import java.util.UUID;

public interface PasswordResetTokenRepository {

    void store(String token, UUID userId, Duration ttl);

    UUID consume(String token);
}
