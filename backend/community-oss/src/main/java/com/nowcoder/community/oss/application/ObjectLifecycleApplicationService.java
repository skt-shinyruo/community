package com.nowcoder.community.oss.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.oss.application.command.DeleteObjectCommand;
import com.nowcoder.community.oss.application.port.ObjectDeletePort;
import com.nowcoder.community.oss.application.result.ObjectLifecycleResult;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectStatus;
import com.nowcoder.community.oss.domain.repository.OssAccessGrantRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectReferenceRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import com.nowcoder.community.oss.domain.service.OssObjectAccessPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Objects;

@Service
public class ObjectLifecycleApplicationService {

    private final OssObjectRepository objectRepository;
    private final ObjectDeletePort deletePort;
    private final Clock clock;
    private final OssObjectAccessPolicy accessPolicy;
    private final ObjectLifecycleTransactionOperations transactionOperations;

    @Autowired
    public ObjectLifecycleApplicationService(
            OssObjectRepository objectRepository,
            ObjectDeletePort deletePort,
            Clock clock,
            OssObjectAccessPolicy accessPolicy,
            ObjectLifecycleTransactionOperations transactionOperations
    ) {
        this.objectRepository = Objects.requireNonNull(objectRepository, "objectRepository must not be null");
        this.deletePort = Objects.requireNonNull(deletePort, "deletePort must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.accessPolicy = accessPolicy == null ? new OssObjectAccessPolicy() : accessPolicy;
        this.transactionOperations = Objects.requireNonNull(
                transactionOperations, "transactionOperations must not be null");
    }

    public ObjectLifecycleApplicationService(
            OssObjectRepository objectRepository,
            OssObjectVersionRepository versionRepository,
            OssObjectReferenceRepository referenceRepository,
            OssAccessGrantRepository grantRepository,
            ObjectDeletePort deletePort,
            Clock clock
    ) {
        this(
                objectRepository,
                deletePort,
                clock,
                new OssObjectAccessPolicy(),
                new ObjectLifecycleTransactionOperations(
                        objectRepository, versionRepository, referenceRepository, grantRepository)
        );
    }

    public ObjectLifecycleResult deleteObject(DeleteObjectCommand command) {
        requireCommand(command);
        OssObject object = objectRepository.findById(command.objectId())
                .orElseThrow(this::objectNotFound);
        if (!accessPolicy.canManage(object, command.actorId())) {
            throw objectNotFound();
        }
        return deleteAuthorizedObject(object);
    }

    public ObjectLifecycleResult deleteInternalObject(
            DeleteObjectCommand command,
            String serviceSubject
    ) {
        requireCommand(command);
        OssObject object = objectRepository.findById(command.objectId())
                .orElseThrow(this::objectNotFound);
        if (serviceSubject == null || serviceSubject.isBlank()
                || !object.ownerService().equals(serviceSubject.trim())
                || "USER".equalsIgnoreCase(object.ownerType())) {
            throw objectNotFound();
        }
        return deleteAuthorizedObject(object);
    }

    private ObjectLifecycleResult deleteAuthorizedObject(OssObject authorizedObject) {
        if (authorizedObject.status() == OssObjectStatus.PURGED) {
            return toResult(authorizedObject, "object already purged");
        }
        ObjectDeletionClaimResult claimResult;
        try {
            claimResult = transactionOperations.claimDeletion(
                    authorizedObject.objectId(), clock.instant());
        } catch (ObjectDeletionTargetNotFoundException notFound) {
            throw objectNotFound();
        }
        if (claimResult.claimedDeletion().isEmpty()) {
            OssObject object = claimResult.object();
            return toResult(
                    object,
                    object.status() == OssObjectStatus.PURGED
                            ? "object purged"
                            : "object delete pending"
            );
        }

        ObjectDeletionClaim claim = claimResult.claimedDeletion().orElseThrow();
        deletePort.deleteIfExists(claim.storageBucket(), claim.storageKey());
        OssObject purged = transactionOperations.finalizeDeletion(claim, clock.instant());
        return toResult(purged, "object purged");
    }

    private void requireCommand(DeleteObjectCommand command) {
        if (command == null || command.objectId() == null) {
            throw new IllegalArgumentException("objectId must not be null");
        }
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
