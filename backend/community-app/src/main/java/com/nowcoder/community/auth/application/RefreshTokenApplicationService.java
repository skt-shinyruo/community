package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.port.RefreshTokenSessionPort;
import com.nowcoder.community.auth.application.result.RefreshCookieSpec;
import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import com.nowcoder.community.auth.domain.service.AuthSecretGenerator;
import com.nowcoder.community.auth.domain.service.RefreshTokenDomainService;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenApplicationService {

    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenStore;
    private final RefreshTokenDomainService refreshTokenDomainService;
    private final AuthSecretGenerator authSecretGenerator;
    private final RefreshTokenSessionPort refreshTokenSessionPort;

    public RefreshTokenApplicationService(
            JwtProperties jwtProperties,
            RefreshTokenRepository refreshTokenStore,
            RefreshTokenDomainService refreshTokenDomainService,
            AuthSecretGenerator authSecretGenerator,
            RefreshTokenSessionPort refreshTokenSessionPort
    ) {
        this.jwtProperties = jwtProperties;
        this.refreshTokenStore = refreshTokenStore;
        this.refreshTokenDomainService = refreshTokenDomainService;
        this.authSecretGenerator = authSecretGenerator;
        this.refreshTokenSessionPort = refreshTokenSessionPort;
    }

    public IssuedRefreshToken issue(UUID userId) {
        String familyId = UUID.randomUUID().toString().replace("-", "");
        return issue(userId, familyId);
    }

    public IssuedRefreshToken rotate(String refreshToken) {
        RefreshTokenRepository.StoredRefreshToken consumed = consume(refreshToken);
        if (consumed == null) {
            return null;
        }
        try {
            return issueInFamily(consumed.userId(), consumed.familyId());
        } catch (RuntimeException e) {
            return null;
        }
    }

    public RefreshTokenRepository.StoredRefreshToken consume(String refreshToken) {
        RefreshTokenRepository.StoredRefreshToken consumed = refreshTokenStore.consume(refreshToken);
        if (consumed == null) {
            maybeRevokeFamilyForReusedToken(refreshToken);
            return null;
        }
        if (refreshTokenDomainService.isExpired(consumed.expiresAt(), Instant.now())) {
            return null;
        }
        return consumed;
    }

    public IssuedRefreshToken issueInFamily(UUID userId, String familyId) {
        return issue(userId, familyId);
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

    public void revokeFamily(String familyId) {
        refreshTokenStore.revokeFamily(familyId);
    }

    public RefreshCookieSpec buildCookie(String refreshToken) {
        return new RefreshCookieSpec(
                jwtProperties.getRefreshCookieName(),
                refreshToken,
                true,
                jwtProperties.isRefreshCookieSecure(),
                jwtProperties.getRefreshCookiePath(),
                jwtProperties.getRefreshCookieSameSite(),
                jwtProperties.getRefreshTokenTtlSeconds()
        );
    }

    public RefreshCookieSpec clearCookie() {
        return new RefreshCookieSpec(
                jwtProperties.getRefreshCookieName(),
                "",
                true,
                jwtProperties.isRefreshCookieSecure(),
                jwtProperties.getRefreshCookiePath(),
                jwtProperties.getRefreshCookieSameSite(),
                0
        );
    }

    public String refreshCookieName() {
        return jwtProperties.getRefreshCookieName();
    }

    public int cleanupExpiredBefore(Instant expiresBefore) {
        return refreshTokenSessionPort.deleteExpiredBefore(expiresBefore);
    }

    private IssuedRefreshToken issue(UUID userId, String familyId) {
        String tokenValue = secureTokenValue();
        Instant expiresAt = Instant.now().plusSeconds(jwtProperties.getRefreshTokenTtlSeconds());
        refreshTokenStore.store(tokenValue, userId, familyId, expiresAt);
        return new IssuedRefreshToken(tokenValue, buildCookie(tokenValue));
    }

    private String secureTokenValue() {
        return authSecretGenerator.opaqueToken();
    }

    private void maybeRevokeFamilyForReusedToken(String refreshToken) {
        RefreshTokenRepository.RevokedRefreshToken revoked = refreshTokenStore.findRevoked(refreshToken);
        if (revoked == null) {
            return;
        }
        Instant now = Instant.now();
        if (refreshTokenDomainService.shouldRevokeFamilyOnReuse(
                revoked.revokedAt(),
                revoked.expiresAt(),
                now,
                jwtProperties.getRefreshReuseGraceSeconds()
        )) {
            refreshTokenStore.revokeFamily(revoked.familyId());
        }
    }

    public record IssuedRefreshToken(String refreshToken, RefreshCookieSpec cookie) {
    }
}
