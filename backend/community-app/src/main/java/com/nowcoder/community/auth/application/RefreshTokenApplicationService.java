package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import com.nowcoder.community.auth.domain.service.RefreshTokenDomainService;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.user.api.action.UserRefreshTokenSessionActionApi;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenApplicationService {

    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenStore;
    private final RefreshTokenDomainService refreshTokenDomainService;
    private final UserRefreshTokenSessionActionApi refreshTokenSessionActionApi;

    public RefreshTokenApplicationService(
            JwtProperties jwtProperties,
            RefreshTokenRepository refreshTokenStore,
            RefreshTokenDomainService refreshTokenDomainService,
            UserRefreshTokenSessionActionApi refreshTokenSessionActionApi
    ) {
        this.jwtProperties = jwtProperties;
        this.refreshTokenStore = refreshTokenStore;
        this.refreshTokenDomainService = refreshTokenDomainService;
        this.refreshTokenSessionActionApi = refreshTokenSessionActionApi;
    }

    public IssuedRefreshToken issue(UUID userId) {
        String familyId = UUID.randomUUID().toString().replace("-", "");
        return issue(userId, familyId);
    }

    public IssuedRefreshToken rotate(String refreshToken) {
        RefreshTokenRepository.StoredRefreshToken consumed = refreshTokenStore.consume(refreshToken);
        if (consumed == null) {
            return null;
        }
        if (refreshTokenDomainService.isExpired(consumed.expiresAt(), Instant.now())) {
            return null;
        }
        try {
            return issue(consumed.userId(), consumed.familyId());
        } catch (RuntimeException e) {
            return null;
        }
    }

    public RefreshTokenRepository.StoredRefreshToken find(String refreshToken) {
        RefreshTokenRepository.StoredRefreshToken token = refreshTokenStore.find(refreshToken);
        if (token == null) {
            return null;
        }
        if (refreshTokenDomainService.isExpired(token.expiresAt(), Instant.now())) {
            refreshTokenStore.revoke(refreshToken);
            return null;
        }
        return token;
    }

    public void revoke(String refreshToken) {
        refreshTokenStore.revoke(refreshToken);
    }

    public void revokeFamilyByToken(String refreshToken) {
        RefreshTokenRepository.StoredRefreshToken token = refreshTokenStore.find(refreshToken);
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

    public String refreshCookieName() {
        return jwtProperties.getRefreshCookieName();
    }

    public int cleanupExpiredBefore(Instant expiresBefore) {
        return refreshTokenSessionActionApi.deleteExpiredBefore(expiresBefore);
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
