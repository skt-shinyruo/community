package com.nowcoder.community.user.application;

import com.nowcoder.community.user.application.result.RefreshTokenSessionResult;
import com.nowcoder.community.user.domain.model.RefreshTokenSession;
import com.nowcoder.community.user.domain.repository.RefreshTokenSessionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenSessionApplicationService {

    private final RefreshTokenSessionRepository repository;

    public RefreshTokenSessionApplicationService(RefreshTokenSessionRepository repository) {
        this.repository = repository;
    }

    public void store(String tokenHash, UUID userId, String familyId, Instant expiresAt) {
        repository.store(tokenHash, userId, familyId, expiresAt);
    }

    public RefreshTokenSessionResult find(String tokenHash) {
        return toResult(repository.find(tokenHash));
    }

    public RefreshTokenSessionResult consume(String tokenHash) {
        return toResult(repository.consumeActive(tokenHash));
    }

    public void revoke(String tokenHash) {
        repository.revoke(tokenHash);
    }

    public int revokeFamily(String familyId) {
        return repository.revokeFamily(familyId);
    }

    public int revokeByUserId(UUID userId) {
        return repository.revokeByUserId(userId);
    }

    public int deleteExpiredBefore(Instant cutoff) {
        return repository.deleteExpiredBefore(cutoff);
    }

    private RefreshTokenSessionResult toResult(RefreshTokenSession session) {
        if (session == null) {
            return null;
        }
        return new RefreshTokenSessionResult(
                session.tokenHash(),
                session.userId(),
                session.familyId(),
                session.expiresAt(),
                session.revokedAt()
        );
    }
}
