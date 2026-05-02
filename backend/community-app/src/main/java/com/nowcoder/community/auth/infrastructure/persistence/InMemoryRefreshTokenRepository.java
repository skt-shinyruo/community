package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "auth.refresh.store", havingValue = "memory", matchIfMissing = false)
public class InMemoryRefreshTokenRepository implements RefreshTokenRepository {

    private final Map<String, StoredRefreshToken> tokens = new ConcurrentHashMap<>();
    private final Map<String, RevokedRefreshToken> revokedTokens = new ConcurrentHashMap<>();

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
        StoredRefreshToken token = tokens.remove(refreshToken);
        if (token != null) {
            revokedTokens.put(refreshToken, new RevokedRefreshToken(
                    refreshToken,
                    token.userId(),
                    token.familyId(),
                    token.expiresAt(),
                    Instant.now()
            ));
        }
        return token;
    }

    @Override
    public RevokedRefreshToken findRevoked(String refreshToken) {
        return revokedTokens.get(refreshToken);
    }

    @Override
    public void revoke(String refreshToken) {
        StoredRefreshToken token = tokens.remove(refreshToken);
        if (token != null) {
            revokedTokens.put(refreshToken, new RevokedRefreshToken(
                    refreshToken,
                    token.userId(),
                    token.familyId(),
                    token.expiresAt(),
                    Instant.now()
            ));
        }
    }

    @Override
    public void revokeFamily(String familyId) {
        tokens.entrySet().removeIf(e -> familyId.equals(e.getValue().familyId()));
    }
}
