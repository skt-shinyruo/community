package com.nowcoder.community.oss.domain.repository;

import com.nowcoder.community.oss.domain.model.OssUploadSession;

import java.util.Optional;
import java.util.List;
import java.time.Instant;
import java.util.UUID;

public interface OssUploadSessionRepository {

    boolean create(OssUploadSession session);

    void save(OssUploadSession session);

    Optional<OssUploadSession> findById(UUID sessionId);

    default Optional<OssUploadSession> findByRequestId(UUID requestId) {
        if (requestId == null) {
            return Optional.empty();
        }
        return findById(requestId).filter(session -> requestId.equals(session.requestId()));
    }

    default boolean claimForCompletion(UUID sessionId, Instant updatedAt) {
        Optional<OssUploadSession> current = findById(sessionId);
        if (current.isEmpty() || current.get().status()
                != com.nowcoder.community.oss.domain.model.OssUploadSessionStatus.READY) {
            return false;
        }
        save(current.get().startUploading(updatedAt));
        return true;
    }

    boolean recordCompletionFailure(
            UUID sessionId,
            long claimVersion,
            String lastError,
            Instant updatedAt
    );

    boolean resetFailedClaim(
            UUID sessionId,
            long claimVersion,
            Instant updatedAt,
            Instant retryExpiresAt
    );

    boolean completeClaim(UUID sessionId, long claimVersion, Instant completedAt);

    default boolean renewReadySession(
            UUID sessionId,
            Instant expectedExpiresAt,
            Instant renewedExpiresAt,
            Instant updatedAt
    ) {
        Optional<OssUploadSession> current = findById(sessionId);
        if (current.isEmpty()
                || current.get().status()
                != com.nowcoder.community.oss.domain.model.OssUploadSessionStatus.READY
                || !current.get().expiresAt().equals(expectedExpiresAt)) {
            return false;
        }
        save(current.get().renewReady(updatedAt, renewedExpiresAt));
        return true;
    }

    default List<OssUploadSession> listRecoverable(Instant updatedBefore, int limit) {
        return List.of();
    }
}
