package com.nowcoder.community.auth.domain.service;

import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class RefreshTokenDomainService {

    public boolean isExpired(Instant expiresAt, Instant now) {
        return expiresAt == null || now == null || expiresAt.isBefore(now);
    }
}
