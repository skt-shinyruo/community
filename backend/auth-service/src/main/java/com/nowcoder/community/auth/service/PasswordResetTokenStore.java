package com.nowcoder.community.auth.service;

import java.time.Duration;

public interface PasswordResetTokenStore {

    void store(String token, int userId, Duration ttl);

    Integer consume(String token);
}
