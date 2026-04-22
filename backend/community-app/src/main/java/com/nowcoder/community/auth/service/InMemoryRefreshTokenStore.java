package com.nowcoder.community.auth.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "auth.refresh.store", havingValue = "memory", matchIfMissing = false)
public class InMemoryRefreshTokenStore implements RefreshTokenStore {

    private final Map<String, StoredRefreshToken> tokens = new ConcurrentHashMap<>();

    @Override
    public void store(String refreshToken, UUID userId, String familyId, Instant expiresAt) {
        tokens.put(refreshToken, new StoredRefreshToken(refreshToken, userId, familyId, expiresAt));
    }

    @Override
    public StoredRefreshToken find(String refreshToken) {
        return tokens.get(refreshToken);
    }

    @Override
    public StoredRefreshToken consume(String refreshToken) {
        return tokens.remove(refreshToken);
    }

    @Override
    public void revoke(String refreshToken) {
        tokens.remove(refreshToken);
    }

    @Override
    public void revokeFamily(String familyId) {
        tokens.entrySet().removeIf(e -> familyId.equals(e.getValue().familyId()));
    }
}
