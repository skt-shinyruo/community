package com.nowcoder.community.oss.application;

import com.nowcoder.community.oss.application.command.GrantObjectAccessCommand;
import com.nowcoder.community.oss.application.command.RevokeObjectAccessCommand;
import com.nowcoder.community.oss.application.result.ObjectAccessDecisionResult;
import com.nowcoder.community.oss.domain.model.OssAccessGrant;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssObjectVersionStatus;
import com.nowcoder.community.oss.domain.repository.OssAccessGrantRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class ObjectPermissionApplicationService {

    private final OssObjectRepository objectRepository;
    private final OssObjectVersionRepository versionRepository;
    private final OssAccessGrantRepository grantRepository;
    private final Clock clock;

    public ObjectPermissionApplicationService(
            OssObjectRepository objectRepository,
            OssObjectVersionRepository versionRepository,
            OssAccessGrantRepository grantRepository,
            Clock clock
    ) {
        this.objectRepository = objectRepository;
        this.versionRepository = versionRepository;
        this.grantRepository = grantRepository;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Transactional
    public ObjectAccessDecisionResult grantAccess(GrantObjectAccessCommand command) {
        requireCommand(command);
        OssObject object = requireObject(command.objectId());
        Instant now = clock.instant();
        UUID versionId = command.versionId() == null ? object.currentVersionId() : command.versionId();
        requireVersionBelongsToObject(object, versionId);
        ensureVersionActive(versionId);
        OssAccessGrant grant = new OssAccessGrant(
                UUID.randomUUID(),
                object.objectId(),
                versionId,
                command.principalType(),
                command.principalValue(),
                command.permission(),
                command.expiresAt(),
                command.actorId(),
                now,
                null
        );
        grantRepository.save(grant);
        return toResult(grant);
    }

    @Transactional
    public ObjectAccessDecisionResult revokeAccess(RevokeObjectAccessCommand command) {
        if (command == null || command.objectId() == null || command.grantId() == null) {
            throw new IllegalArgumentException("objectId and grantId must not be null");
        }
        requireObject(command.objectId());
        OssAccessGrant grant = grantRepository.findById(command.grantId())
                .orElseThrow(() -> new IllegalArgumentException("grant not found"));
        if (!grant.objectId().equals(command.objectId())) {
            throw new IllegalArgumentException("grant does not belong to object");
        }
        OssAccessGrant revoked = grant.revoke(clock.instant());
        grantRepository.save(revoked);
        return toResult(revoked);
    }

    private void requireCommand(GrantObjectAccessCommand command) {
        if (command == null || command.objectId() == null) {
            throw new IllegalArgumentException("objectId must not be null");
        }
        requireText(command.principalType(), "principalType");
        requireText(command.principalValue(), "principalValue");
        requireText(command.permission(), "permission");
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
            throw new IllegalStateException("object version is not available for grant");
        }
    }

    private ObjectAccessDecisionResult toResult(OssAccessGrant grant) {
        return new ObjectAccessDecisionResult(
                grant.grantId(),
                grant.objectId(),
                grant.versionId(),
                grant.principalType(),
                grant.principalValue(),
                grant.permission(),
                grant.expiresAt(),
                grant.createdBy(),
                grant.createdAt(),
                grant.revokedAt(),
                grant.activeAt(clock.instant())
        );
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
