package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.result.RefreshCookieSpec;
import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import com.nowcoder.community.auth.domain.service.AuthSecretGenerator;
import com.nowcoder.community.auth.domain.service.RefreshTokenDomainService;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class RefreshTokenApplicationService {

    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenStore;
    private final RefreshTokenDomainService refreshTokenDomainService;
    private final AuthSecretGenerator authSecretGenerator;
    private final Clock clock;

    public RefreshTokenApplicationService(
            JwtProperties jwtProperties,
            RefreshTokenRepository refreshTokenStore,
            RefreshTokenDomainService refreshTokenDomainService,
            AuthSecretGenerator authSecretGenerator,
            Clock clock
    ) {
        this.jwtProperties = Objects.requireNonNull(jwtProperties, "jwtProperties must not be null");
        this.refreshTokenStore = Objects.requireNonNull(refreshTokenStore, "refreshTokenStore must not be null");
        this.refreshTokenDomainService = Objects.requireNonNull(
                refreshTokenDomainService, "refreshTokenDomainService must not be null");
        this.authSecretGenerator = Objects.requireNonNull(
                authSecretGenerator, "authSecretGenerator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public IssuedRefreshToken issue(UUID userId, long securityVersionAtIssue) {
        String familyId = UUID.randomUUID().toString().replace("-", "");
        return issue(userId, familyId, securityVersionAtIssue);
    }

    @Transactional
    public RefreshTokenRepository.StoredRefreshToken beginRotation(String refreshToken) {
        Instant now = clock.instant();
        UUID rotationLeaseId = UUID.randomUUID();
        RefreshTokenRepository.StoredRefreshToken pending = refreshTokenStore.beginRotation(
                refreshToken,
                now.plusSeconds(30),
                rotationLeaseId
        );
        if (pending == null) {
            maybeRevokeFamilyForReusedToken(refreshToken);
            return null;
        }
        if (refreshTokenDomainService.isExpired(pending.expiresAt(), now)) {
            rollbackPendingRotation(refreshToken, pending.rotationLeaseId());
            return null;
        }
        return pending;
    }

    public IssuedRefreshToken generateReplacementToken(UUID userId, String familyId) {
        String tokenValue = secureTokenValue();
        return new IssuedRefreshToken(tokenValue, buildCookie(tokenValue));
    }

    @Transactional
    public boolean finishRotation(
            String pendingRefreshToken,
            String replacementRefreshToken,
            UUID userId,
            String familyId,
            long securityVersionAtIssue,
            UUID rotationLeaseId
    ) {
        Instant replacementExpiresAt = clock.instant().plusSeconds(jwtProperties.getRefreshTokenTtlSeconds());
        if (rotationLeaseId == null) {
            return false;
        }
        return refreshTokenStore.finishRotation(
                pendingRefreshToken,
                replacementRefreshToken,
                userId,
                familyId,
                securityVersionAtIssue,
                replacementExpiresAt,
                rotationLeaseId
        );
    }

    public boolean rollbackPendingRotation(String refreshToken, UUID rotationLeaseId) {
        return rotationLeaseId != null
                && refreshTokenStore.rollbackPendingRotation(refreshToken, rotationLeaseId);
    }

    public RefreshTokenRepository.StoredRefreshToken find(String refreshToken) {
        RefreshTokenRepository.StoredRefreshToken token = refreshTokenStore.find(refreshToken);
        if (token == null) {
            return null;
        }
        if (refreshTokenDomainService.isExpired(token.expiresAt(), clock.instant())) {
            refreshTokenStore.revoke(refreshToken);
            return null;
        }
        return token;
    }

    public void revoke(String refreshToken) {
        refreshTokenStore.revoke(refreshToken);
    }

    @Transactional
    public void revokeFamilyByToken(String refreshToken) {
        revokeFamilyByPresentedTokenCore(refreshToken);
    }

    @Transactional
    public void revokeFamilyByPresentedToken(String refreshToken) {
        revokeFamilyByPresentedTokenCore(refreshToken);
    }

    private void revokeFamilyByPresentedTokenCore(String refreshToken) {
        refreshTokenStore.revokeFamilyByPresentedToken(refreshToken);
    }

    @Transactional
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
        return refreshTokenStore.deleteExpiredBefore(expiresBefore);
    }

    private IssuedRefreshToken issue(UUID userId, String familyId, long securityVersionAtIssue) {
        String tokenValue = secureTokenValue();
        Instant expiresAt = clock.instant().plusSeconds(jwtProperties.getRefreshTokenTtlSeconds());
        refreshTokenStore.store(tokenValue, userId, familyId, securityVersionAtIssue, expiresAt);
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
        Instant now = clock.instant();
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
