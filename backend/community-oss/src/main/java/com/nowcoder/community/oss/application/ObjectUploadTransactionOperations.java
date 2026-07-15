package com.nowcoder.community.oss.application;

import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssUploadSession;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import com.nowcoder.community.oss.domain.repository.OssUploadSessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class ObjectUploadTransactionOperations {

    private final OssObjectRepository objectRepository;
    private final OssObjectVersionRepository versionRepository;
    private final OssUploadSessionRepository sessionRepository;

    public ObjectUploadTransactionOperations(
            OssObjectRepository objectRepository,
            OssObjectVersionRepository versionRepository,
            OssUploadSessionRepository sessionRepository
    ) {
        this.objectRepository = Objects.requireNonNull(objectRepository, "objectRepository must not be null");
        this.versionRepository = Objects.requireNonNull(versionRepository, "versionRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean createPreparedUpload(
            OssObject object,
            OssObjectVersion version,
            OssUploadSession session
    ) {
        if (!sessionRepository.create(session)) {
            return false;
        }
        if (!objectRepository.create(object)) {
            throw new IllegalStateException("prepared upload object already exists without its request owner");
        }
        if (!versionRepository.create(version)) {
            throw new IllegalStateException("prepared upload version already exists without its request owner");
        }
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<OssUploadSession> claimCompletion(UUID sessionId, Instant updatedAt) {
        if (!sessionRepository.claimForCompletion(sessionId, updatedAt)) {
            return Optional.empty();
        }
        OssUploadSession claimed = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalStateException("claimed upload session cannot be reloaded"));
        return Optional.of(claimed);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordCompletionFailure(OssUploadSession session) {
        return sessionRepository.recordCompletionFailure(
                session.sessionId(),
                session.claimVersion(),
                session.lastError(),
                session.updatedAt()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean renewReadySession(
            UUID sessionId,
            Instant expectedExpiresAt,
            Instant renewedExpiresAt,
            Instant updatedAt
    ) {
        return sessionRepository.renewReadySession(
                sessionId, expectedExpiresAt, renewedExpiresAt, updatedAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordRecoveryFailure(
            OssUploadSession session,
            String lastError,
            Instant observedAt
    ) {
        return sessionRepository.recordCompletionFailure(
                session.sessionId(),
                session.claimVersion(),
                lastError,
                observedAt
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean resetFailedClaim(
            UUID sessionId,
            long claimVersion,
            Instant updatedAt,
            Instant retryExpiresAt
    ) {
        return sessionRepository.resetFailedClaim(
                sessionId, claimVersion, updatedAt, retryExpiresAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeUpload(
            OssObjectVersion version,
            OssObject object,
            OssUploadSession session
    ) {
        if (!sessionRepository.completeClaim(
                session.sessionId(), session.claimVersion(), session.completedAt())) {
            throw new IllegalStateException("upload finalize lost its fenced session claim");
        }
        versionRepository.save(version);
        objectRepository.save(object);
    }
}
