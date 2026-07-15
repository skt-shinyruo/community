package com.nowcoder.community.oss.application;

import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssUploadSession;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import com.nowcoder.community.oss.domain.repository.OssUploadSessionRepository;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStore;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStoreObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ObjectUploadRecoveryApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ObjectUploadRecoveryApplicationService.class);

    private final OssObjectRepository objectRepository;
    private final OssObjectVersionRepository versionRepository;
    private final OssUploadSessionRepository sessionRepository;
    private final ObjectStore objectStore;
    private final Clock clock;
    private final ObjectUploadTransactionOperations transactionOperations;

    @Autowired
    public ObjectUploadRecoveryApplicationService(
            OssObjectRepository objectRepository,
            OssObjectVersionRepository versionRepository,
            OssUploadSessionRepository sessionRepository,
            ObjectStore objectStore,
            Clock clock,
            ObjectUploadTransactionOperations transactionOperations
    ) {
        this.objectRepository = Objects.requireNonNull(objectRepository, "objectRepository must not be null");
        this.versionRepository = Objects.requireNonNull(versionRepository, "versionRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.objectStore = Objects.requireNonNull(objectStore, "objectStore must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.transactionOperations = Objects.requireNonNull(
                transactionOperations, "transactionOperations must not be null");
    }

    public ObjectUploadRecoveryApplicationService(
            OssObjectRepository objectRepository,
            OssObjectVersionRepository versionRepository,
            OssUploadSessionRepository sessionRepository,
            ObjectStore objectStore,
            Clock clock
    ) {
        this(
                objectRepository,
                versionRepository,
                sessionRepository,
                objectStore,
                clock,
                new ObjectUploadTransactionOperations(
                        objectRepository, versionRepository, sessionRepository)
        );
    }

    public void recoverStaleUploads(Instant updatedBefore, int limit) {
        if (updatedBefore == null) {
            return;
        }
        List<OssUploadSession> sessions = sessionRepository.listRecoverable(updatedBefore, limit);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        for (OssUploadSession session : sessions) {
            try {
                recover(session);
            } catch (RuntimeException failure) {
                safelyRecordRecoveryFailure(session, failure);
                log.warn(
                        "[oss-upload] failed to recover session {} claim {}: {}",
                        session.sessionId(),
                        session.claimVersion(),
                        failure.toString()
                );
            }
        }
    }

    private void recover(OssUploadSession session) {
        OssObject object = objectRepository.findById(session.objectId()).orElse(null);
        OssObjectVersion version = versionRepository.findById(session.versionId()).orElse(null);
        if (object == null || version == null || !object.objectId().equals(version.objectId())) {
            safelyRecordRecoveryFailure(
                    session,
                    new IllegalStateException("upload metadata is missing or inconsistent")
            );
            return;
        }
        String attemptStorageKey = ObjectUploadApplicationService.attemptStorageKey(session);
        Optional<ObjectStoreObject> stored = objectStore.head(version.storageBucket(), attemptStorageKey);
        if (stored.isEmpty()) {
            Instant resetAt = clock.instant();
            transactionOperations.resetFailedClaim(
                    session.sessionId(),
                    session.claimVersion(),
                    resetAt,
                    resetAt.plus(retryTtl(session))
            );
            return;
        }
        Instant now = clock.instant();
        ObjectStoreObject metadata = stored.get();
        ObjectUploadApplicationService.validateStoredMetadata(session, metadata);
        OssObjectVersion activatedVersion = version.withUploadedContentAt(
                attemptStorageKey,
                metadata.contentType(),
                metadata.contentLength(),
                session.expectedChecksumSha256()
        ).activate(metadata.etag(), now);
        OssObject activatedObject = object.activate(activatedVersion, now);
        OssUploadSession completedSession = session.complete(now);
        transactionOperations.finalizeUpload(activatedVersion, activatedObject, completedSession);
    }

    private void safelyRecordRecoveryFailure(OssUploadSession session, RuntimeException failure) {
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        String detail = "RECOVERY_FAILED:" + failure.getClass().getSimpleName() + ":" + message
                .replace('\n', ' ')
                .replace('\r', ' ');
        try {
            transactionOperations.recordRecoveryFailure(
                    session,
                    detail.length() <= 512 ? detail : detail.substring(0, 512),
                    clock.instant()
            );
        } catch (RuntimeException observationFailure) {
            log.warn(
                    "[oss-upload] failed to record recovery observation for session {} claim {}: {}",
                    session.sessionId(),
                    session.claimVersion(),
                    observationFailure.toString()
            );
        }
    }

    private static Duration retryTtl(OssUploadSession session) {
        Duration original = Duration.between(session.createdAt(), session.expiresAt());
        return original.isNegative() || original.isZero() ? Duration.ofMinutes(15) : original;
    }
}
