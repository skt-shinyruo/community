package com.nowcoder.community.auth.domain.service;

import java.time.Instant;

public class RefreshTokenDomainService {

    public boolean isExpired(Instant expiresAt, Instant now) {
        return expiresAt == null || now == null || expiresAt.isBefore(now);
    }
}
