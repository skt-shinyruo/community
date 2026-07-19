package com.nowcoder.community.oss.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.oss.application.command.DeleteObjectCommand;
import com.nowcoder.community.oss.application.result.ObjectLifecycleResult;
import com.nowcoder.community.oss.domain.model.OssAccessGrant;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectReference;
import com.nowcoder.community.oss.domain.model.OssObjectStatus;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.repository.OssAccessGrantRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectReferenceRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import com.nowcoder.community.oss.domain.service.OssObjectAccessPolicy;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ObjectLifecycleApplicationService {

    private final OssObjectRepository objectRepository;
    private final OssObjectVersionRepository versionRepository;
    private final OssObjectReferenceRepository referenceRepository;
    private final OssAccessGrantRepository grantRepository;
    private final ObjectStore objectStore;
    private final Clock clock;
    private final OssObjectAccessPolicy accessPolicy;

    public ObjectLifecycleApplicationService(
            OssObjectRepository objectRepository,
            OssObjectVersionRepository versionRepository,
            OssObjectReferenceRepository referenceRepository,
            OssAccessGrantRepository grantRepository,
            ObjectStore objectStore,
            Clock clock
    ) {
        this(
                objectRepository,
                versionRepository,
                referenceRepository,
                grantRepository,
                objectStore,
                clock,
                new OssObjectAccessPolicy()
        );
    }

    @Autowired
    public ObjectLifecycleApplicationService(
            OssObjectRepository objectRepository,
            OssObjectVersionRepository versionRepository,
            OssObjectReferenceRepository referenceRepository,
            OssAccessGrantRepository grantRepository,
            ObjectStore objectStore,
            Clock clock,
            OssObjectAccessPolicy accessPolicy
    ) {
        this.objectRepository = objectRepository;
        this.versionRepository = versionRepository;
        this.referenceRepository = referenceRepository;
        this.grantRepository = grantRepository;
        this.objectStore = objectStore;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.accessPolicy = accessPolicy == null ? new OssObjectAccessPolicy() : accessPolicy;
    }

    @Transactional
    public ObjectLifecycleResult deleteObject(DeleteObjectCommand command) {
        if (command == null || command.objectId() == null) {
            throw new IllegalArgumentException("objectId must not be null");
        }
        OssObject object = objectRepository.findById(command.objectId())
                .orElseThrow(this::objectNotFound);
        if (!accessPolicy.canManage(object, command.actorId())) {
            throw objectNotFound();
        }
        return deleteObject(object);
    }

    private ObjectLifecycleResult deleteObject(OssObject object) {
        OssObjectVersion currentVersion = findCurrentVersion(object);
        Instant now = clock.instant();
        if (object.status() == OssObjectStatus.PURGED) {
            return toResult(object, "object already purged");
        }

        List<OssObjectReference> activeReferences = referenceRepository.findByObjectId(object.objectId()).stream()
                .filter(reference -> reference.activeAt(now))
                .toList();
        List<OssAccessGrant> activeGrants = grantRepository.findByObjectId(object.objectId()).stream()
                .filter(grant -> grant.activeAt(now))
                .toList();
        if (!activeReferences.isEmpty() || !activeGrants.isEmpty()) {
            OssObject deletePending = object.deletePending(now);
            objectRepository.save(deletePending);
            return toResult(deletePending, "object delete pending");
        }

        if (currentVersion != null) {
            objectStore.delete(currentVersion.storageBucket(), currentVersion.storageKey());
            versionRepository.save(currentVersion.purge(now));
        }
        OssObject purged = object.purge(now);
        objectRepository.save(purged);
        return toResult(purged, "object purged");
    }

    @Transactional
    public ObjectLifecycleResult deleteInternalObject(
            DeleteObjectCommand command,
            String serviceSubject
    ) {
        OssObject object = objectRepository.findById(command.objectId())
                .orElseThrow(this::objectNotFound);
        if (serviceSubject == null || serviceSubject.isBlank()
                || !object.ownerService().equals(serviceSubject.trim())
                || "USER".equalsIgnoreCase(object.ownerType())) {
            throw objectNotFound();
        }
        return deleteObject(object);
    }

    private OssObjectVersion findCurrentVersion(OssObject object) {
        UUID currentVersionId = object.currentVersionId();
        if (currentVersionId == null) {
            return null;
        }
        OssObjectVersion version = versionRepository.findById(currentVersionId)
                .orElseThrow(this::objectNotFound);
        if (!object.objectId().equals(version.objectId())) {
            throw objectNotFound();
        }
        return version;
    }

    private ObjectLifecycleResult toResult(OssObject object, String message) {
        return new ObjectLifecycleResult(
                object.objectId(),
                object.currentVersionId(),
                object.status().name(),
                object.status() == OssObjectStatus.DELETE_PENDING,
                object.status() == OssObjectStatus.PURGED,
                message,
                object.updatedAt()
        );
    }

    private BusinessException objectNotFound() {
        return new BusinessException(CommonErrorCode.NOT_FOUND, "OSS object not found");
    }
}
