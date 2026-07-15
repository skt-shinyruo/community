package com.nowcoder.community.oss.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.ErrorKind;
import com.nowcoder.community.common.exception.SimpleErrorCode;
import com.nowcoder.community.oss.application.command.BindObjectReferenceCommand;
import com.nowcoder.community.oss.application.command.ReleaseObjectReferenceCommand;
import com.nowcoder.community.oss.application.result.ObjectReferenceResult;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectReference;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssObjectVersionStatus;
import com.nowcoder.community.oss.domain.repository.OssObjectReferenceRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class ObjectReferenceApplicationService {

    private static final SimpleErrorCode REFERENCE_SEMANTIC_CONFLICT =
            new SimpleErrorCode(40901, "object reference semantic conflict", ErrorKind.CONFLICT);

    private final OssObjectRepository objectRepository;
    private final OssObjectVersionRepository versionRepository;
    private final OssObjectReferenceRepository referenceRepository;
    private final Clock clock;

    public ObjectReferenceApplicationService(
            OssObjectRepository objectRepository,
            OssObjectVersionRepository versionRepository,
            OssObjectReferenceRepository referenceRepository,
            Clock clock
    ) {
        this.objectRepository = objectRepository;
        this.versionRepository = versionRepository;
        this.referenceRepository = referenceRepository;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Transactional
    public ObjectReferenceResult bindReference(BindObjectReferenceCommand command) {
        requireCommand(command);
        Instant now = clock.instant();
        if (command.referenceId() != null) {
            var existing = referenceRepository.findById(command.referenceId());
            if (existing.isPresent()) {
                OssObjectReference stored = existing.orElseThrow();
                return replayOrConflict(stored, requestedReference(
                        command,
                        command.versionId() == null ? stored.versionId() : command.versionId(),
                        now
                ));
            }
        }

        OssObject object = requireObject(command.objectId());
        UUID versionId = command.versionId() == null ? object.currentVersionId() : command.versionId();
        requireVersionBelongsToObject(object, versionId);
        ensureVersionActive(versionId);
        OssObjectReference reference = requestedReference(command, versionId, now);
        return replayOrConflict(referenceRepository.insertOrFindExisting(reference), reference);
    }

    @Transactional
    public ObjectReferenceResult releaseReference(ReleaseObjectReferenceCommand command) {
        if (command == null || command.objectId() == null || command.referenceId() == null) {
            throw new IllegalArgumentException("objectId and referenceId must not be null");
        }
        requireObject(command.objectId());
        OssObjectReference reference = referenceRepository.findById(command.referenceId())
                .orElseThrow(() -> new IllegalArgumentException("reference not found"));
        if (!reference.objectId().equals(command.objectId())) {
            throw new IllegalArgumentException("reference does not belong to object");
        }
        OssObjectReference released = reference.release(clock.instant());
        if (released != reference) {
            referenceRepository.save(released);
        }
        return toResult(released);
    }

    @Transactional(readOnly = true)
    public ObjectReferenceResult findReference(UUID objectId, UUID referenceId) {
        if (objectId == null || referenceId == null) {
            throw new IllegalArgumentException("objectId and referenceId must not be null");
        }
        return referenceRepository.findById(referenceId)
                .filter(reference -> reference.objectId().equals(objectId))
                .map(this::toResult)
                .orElse(null);
    }

    private ObjectReferenceResult replayOrConflict(
            OssObjectReference existing,
            OssObjectReference requested
    ) {
        if (!sameSemanticFingerprint(existing, requested)) {
            throw new BusinessException(REFERENCE_SEMANTIC_CONFLICT);
        }
        return toResult(existing);
    }

    private OssObjectReference requestedReference(
            BindObjectReferenceCommand command,
            UUID versionId,
            Instant now
    ) {
        return OssObjectReference.active(
                command.referenceId() == null ? UUID.randomUUID() : command.referenceId(),
                command.objectId(),
                versionId,
                command.subjectService(),
                command.subjectDomain(),
                command.subjectType(),
                command.subjectId(),
                command.referenceRole(),
                now,
                command.retainUntil()
        );
    }

    private boolean sameSemanticFingerprint(
            OssObjectReference existing,
            OssObjectReference requested
    ) {
        return Objects.equals(existing.objectId(), requested.objectId())
                && Objects.equals(existing.versionId(), requested.versionId())
                && Objects.equals(existing.subjectService(), requested.subjectService())
                && Objects.equals(existing.subjectDomain(), requested.subjectDomain())
                && Objects.equals(existing.subjectType(), requested.subjectType())
                && Objects.equals(existing.subjectId(), requested.subjectId())
                && Objects.equals(existing.referenceRole(), requested.referenceRole())
                && Objects.equals(existing.retainUntil(), requested.retainUntil());
    }

    private void requireCommand(BindObjectReferenceCommand command) {
        if (command == null || command.objectId() == null) {
            throw new IllegalArgumentException("objectId must not be null");
        }
        requireText(command.subjectService(), "subjectService");
        requireText(command.subjectDomain(), "subjectDomain");
        requireText(command.subjectType(), "subjectType");
        requireText(command.subjectId(), "subjectId");
        requireText(command.referenceRole(), "referenceRole");
    }

    private OssObject requireObject(UUID objectId) {
        return objectRepository.findById(objectId)
                .orElseThrow(() -> new IllegalArgumentException("object not found"));
    }

    private void requireVersionBelongsToObject(OssObject object, UUID versionId) {
        if (versionId == null) {
            return;
        }
        OssObjectVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("object version not found"));
        if (!object.objectId().equals(version.objectId())) {
            throw new IllegalArgumentException("object version does not belong to object");
        }
    }

    private void ensureVersionActive(UUID versionId) {
        if (versionId == null) {
            return;
        }
        OssObjectVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("object version not found"));
        if (version.status() != OssObjectVersionStatus.ACTIVE) {
            throw new IllegalStateException("object version is not available for reference");
        }
    }

    private ObjectReferenceResult toResult(OssObjectReference reference) {
        return new ObjectReferenceResult(
                reference.referenceId(),
                reference.objectId(),
                reference.versionId(),
                reference.subjectService(),
                reference.subjectDomain(),
                reference.subjectType(),
                reference.subjectId(),
                reference.referenceRole(),
                reference.status().name(),
                reference.retainUntil(),
                reference.createdAt(),
                reference.releasedAt()
        );
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
