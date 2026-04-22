package com.nowcoder.community.auth.service;

import java.time.Duration;
import java.util.UUID;

public interface PasswordResetTokenStore {

    void store(String token, UUID userId, Duration ttl);

    UUID consume(String token);
}
