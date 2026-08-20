package com.nowcoder.community.oss.infrastructure.persistence.dataobject;

import com.nowcoder.community.oss.domain.model.OssObjectReference;
import com.nowcoder.community.oss.domain.model.OssObjectReferenceStatus;

import java.time.Instant;
import java.util.UUID;

public record OssObjectReferenceDataObject(
        UUID referenceId,
        UUID objectId,
        UUID versionId,
        String subjectService,
        String subjectDomain,
        String subjectType,
        String subjectId,
        String referenceRole,
        String status,
        Instant retainUntil,
        Instant createdAt,
        Instant releasedAt
) {

    public static OssObjectReferenceDataObject from(OssObjectReference reference) {
        return new OssObjectReferenceDataObject(
                reference.referenceId(), reference.objectId(), reference.versionId(), reference.subjectService(),
                reference.subjectDomain(), reference.subjectType(), reference.subjectId(), reference.referenceRole(),
                reference.status().name(), reference.retainUntil(), reference.createdAt(), reference.releasedAt()
        );
    }

    public OssObjectReference toDomain() {
        return new OssObjectReference(
                referenceId, objectId, versionId, subjectService, subjectDomain, subjectType, subjectId,
                referenceRole, OssObjectReferenceStatus.valueOf(status), retainUntil, createdAt, releasedAt
        );
    }
}
