package com.nowcoder.community.auth.domain.service;

import java.time.Duration;
import java.time.Instant;

public class RefreshTokenDomainService {

    public boolean isExpired(Instant expiresAt, Instant now) {
        return expiresAt == null || now == null || expiresAt.isBefore(now);
    }

    public boolean shouldRevokeFamilyOnReuse(Instant revokedAt, Instant expiresAt, Instant now, long graceSeconds) {
        if (revokedAt == null || expiresAt == null || now == null || !expiresAt.isAfter(now)) {
            return false;
        }
        long normalizedGraceSeconds = Math.max(0, graceSeconds);
        return Duration.between(revokedAt, now).compareTo(Duration.ofSeconds(normalizedGraceSeconds)) > 0;
    }
}
