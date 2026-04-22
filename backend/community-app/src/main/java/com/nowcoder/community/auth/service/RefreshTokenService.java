package com.nowcoder.community.auth.service;

import com.nowcoder.community.common.security.jwt.JwtProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final JwtProperties jwtProperties;
    private final RefreshTokenStore refreshTokenStore;

    public RefreshTokenService(JwtProperties jwtProperties, RefreshTokenStore refreshTokenStore) {
        this.jwtProperties = jwtProperties;
        this.refreshTokenStore = refreshTokenStore;
    }

    public IssuedRefreshToken issue(UUID userId) {
        String familyId = UUID.randomUUID().toString().replace("-", "");
        return issue(userId, familyId);
    }

    public IssuedRefreshToken rotate(String refreshToken) {
        RefreshTokenStore.StoredRefreshToken consumed = refreshTokenStore.consume(refreshToken);
        if (consumed == null) {
            return null;
        }
        if (consumed.expiresAt().isBefore(Instant.now())) {
            return null;
        }
        try {
            return issue(consumed.userId(), consumed.familyId());
        } catch (RuntimeException e) {
            return null;
        }
    }

    public RefreshTokenStore.StoredRefreshToken find(String refreshToken) {
        RefreshTokenStore.StoredRefreshToken token = refreshTokenStore.find(refreshToken);
        if (token == null) {
            return null;
        }
        if (token.expiresAt().isBefore(Instant.now())) {
            refreshTokenStore.revoke(refreshToken);
            return null;
        }
        return token;
    }

    public void revoke(String refreshToken) {
        refreshTokenStore.revoke(refreshToken);
    }

    public void revokeFamilyByToken(String refreshToken) {
        RefreshTokenStore.StoredRefreshToken token = refreshTokenStore.find(refreshToken);
        refreshTokenStore.revoke(refreshToken);
        if (token != null) {
            refreshTokenStore.revokeFamily(token.familyId());
        }
    }

    public ResponseCookie buildCookie(String refreshToken) {
        return ResponseCookie.from(jwtProperties.getRefreshCookieName(), refreshToken)
                .httpOnly(true)
                .secure(jwtProperties.isRefreshCookieSecure())
                .path(jwtProperties.getRefreshCookiePath())
                .sameSite(jwtProperties.getRefreshCookieSameSite())
                .maxAge(jwtProperties.getRefreshTokenTtlSeconds())
                .build();
    }

    public ResponseCookie clearCookie() {
        return ResponseCookie.from(jwtProperties.getRefreshCookieName(), "")
                .httpOnly(true)
                .secure(jwtProperties.isRefreshCookieSecure())
                .path(jwtProperties.getRefreshCookiePath())
                .sameSite(jwtProperties.getRefreshCookieSameSite())
                .maxAge(0)
                .build();
    }

    private IssuedRefreshToken issue(UUID userId, String familyId) {
        String tokenValue = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plusSeconds(jwtProperties.getRefreshTokenTtlSeconds());
        refreshTokenStore.store(tokenValue, userId, familyId, expiresAt);
        return new IssuedRefreshToken(tokenValue, buildCookie(tokenValue));
    }

    public record IssuedRefreshToken(String refreshToken, ResponseCookie cookie) {
    }
}
