package com.nowcoder.community.auth.infrastructure.persistence.dataobject;

import com.nowcoder.community.auth.domain.model.RefreshTokenSession;
import com.nowcoder.community.auth.domain.model.RefreshTokenSessionState;

import java.time.Instant;
import java.util.UUID;

public class RefreshTokenSessionDataObject {

    private String tokenHash;
    private UUID userId;
    private String familyId;
    private long securityVersionAtIssue;
    private Instant expiresAt;
    private RefreshTokenSessionState state;
    private Instant pendingExpiresAt;
    private Instant revokedAt;

    public RefreshTokenSession toDomain() {
        return new RefreshTokenSession(
                tokenHash,
                userId,
                familyId,
                securityVersionAtIssue,
                expiresAt,
                revokedAt,
                state,
                pendingExpiresAt
        );
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getFamilyId() {
        return familyId;
    }

    public void setFamilyId(String familyId) {
        this.familyId = familyId;
    }

    public long getSecurityVersionAtIssue() {
        return securityVersionAtIssue;
    }

    public void setSecurityVersionAtIssue(long securityVersionAtIssue) {
        this.securityVersionAtIssue = securityVersionAtIssue;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public RefreshTokenSessionState getState() {
        return state;
    }

    public void setState(RefreshTokenSessionState state) {
        this.state = state;
    }

    public Instant getPendingExpiresAt() {
        return pendingExpiresAt;
    }

    public void setPendingExpiresAt(Instant pendingExpiresAt) {
        this.pendingExpiresAt = pendingExpiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
