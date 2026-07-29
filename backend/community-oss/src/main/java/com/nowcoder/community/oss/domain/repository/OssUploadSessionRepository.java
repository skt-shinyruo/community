package com.nowcoder.community.oss.domain.repository;

import com.nowcoder.community.oss.domain.model.OssUploadSession;
import com.nowcoder.community.oss.domain.model.OssUploadSessionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
        if (current.isEmpty() || current.get().status() != OssUploadSessionStatus.READY) {
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

    default boolean cancelActiveSession(
            UUID sessionId,
            UUID objectId,
            UUID versionId,
            Instant updatedAt,
            Instant cleanupAfter
    ) {
        Optional<OssUploadSession> current = findById(sessionId);
        if (current.isEmpty()
                || !current.get().objectId().equals(objectId)
                || !current.get().versionId().equals(versionId)) {
            return false;
        }
        OssUploadSessionStatus status = current.get().status();
        if (status != OssUploadSessionStatus.READY
                && status != OssUploadSessionStatus.UPLOADING
                && status != OssUploadSessionStatus.EXPIRED) {
            return false;
        }
        save(current.get().cancel(updatedAt, cleanupAfter));
        return true;
    }

    default boolean recordCancellationCleanup(
            UUID sessionId,
            long claimVersion,
            String lastError,
            Instant updatedAt
    ) {
        Optional<OssUploadSession> current = findById(sessionId);
        if (current.isEmpty()
                || current.get().status() != OssUploadSessionStatus.CANCELLED_CLEANUP_PENDING
                || current.get().claimVersion() != claimVersion) {
            return false;
        }
        save(current.get().recordCancellationCleanup(updatedAt, lastError));
        return true;
    }

    default boolean completeCancellationCleanup(
            UUID sessionId,
            long claimVersion,
            Instant completedAt
    ) {
        Optional<OssUploadSession> current = findById(sessionId);
        if (current.isEmpty()
                || current.get().status() != OssUploadSessionStatus.CANCELLED_CLEANUP_PENDING
                || current.get().claimVersion() != claimVersion) {
            return false;
        }
        save(current.get().completeCancellationCleanup(completedAt));
        return true;
    }

    default boolean renewReadySession(
            UUID sessionId,
            Instant expectedExpiresAt,
            Instant renewedExpiresAt,
            Instant updatedAt
    ) {
        Optional<OssUploadSession> current = findById(sessionId);
        if (current.isEmpty()
                || current.get().status() != OssUploadSessionStatus.READY
                || !current.get().expiresAt().equals(expectedExpiresAt)) {
            return false;
        }
        save(current.get().renewReady(updatedAt, renewedExpiresAt));
        return true;
    }

    default List<OssUploadSession> listRecoverable(
            Instant uploadingUpdatedBefore,
            Instant cancellationUpdatedBefore,
            int limit
    ) {
        return List.of();
    }
}
