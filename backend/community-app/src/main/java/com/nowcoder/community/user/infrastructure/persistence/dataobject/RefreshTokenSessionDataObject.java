package com.nowcoder.community.user.infrastructure.persistence.dataobject;

import com.nowcoder.community.user.domain.model.RefreshTokenSession;

import java.time.Instant;
import java.util.UUID;

public class RefreshTokenSessionDataObject {

    private String tokenHash;
    private UUID userId;
    private String familyId;
    private Instant expiresAt;
    private Instant revokedAt;

    public RefreshTokenSession toDomain() {
        return new RefreshTokenSession(tokenHash, userId, familyId, expiresAt, revokedAt);
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

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
