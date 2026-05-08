package com.nowcoder.community.oss.application;

import com.nowcoder.community.oss.application.command.BindObjectReferenceCommand;
import com.nowcoder.community.oss.application.command.ReleaseObjectReferenceCommand;
import com.nowcoder.community.oss.application.result.ObjectReferenceResult;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectReference;
import com.nowcoder.community.oss.domain.repository.OssObjectReferenceRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class ObjectReferenceApplicationService {

    private final OssObjectRepository objectRepository;
    private final OssObjectReferenceRepository referenceRepository;
    private final Clock clock;

    public ObjectReferenceApplicationService(
            OssObjectRepository objectRepository,
            OssObjectReferenceRepository referenceRepository,
            Clock clock
    ) {
        this.objectRepository = objectRepository;
        this.referenceRepository = referenceRepository;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Transactional
    public ObjectReferenceResult bindReference(BindObjectReferenceCommand command) {
        requireCommand(command);
        OssObject object = requireObject(command.objectId());
        Instant now = clock.instant();
        UUID versionId = command.versionId() == null ? object.currentVersionId() : command.versionId();
        OssObjectReference reference = OssObjectReference.active(
                UUID.randomUUID(),
                object.objectId(),
                versionId,
                command.subjectService(),
                command.subjectDomain(),
                command.subjectType(),
                command.subjectId(),
                command.referenceRole(),
                now,
                command.retainUntil()
        );
        referenceRepository.save(reference);
        return toResult(reference);
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
        referenceRepository.save(released);
        return toResult(released);
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
