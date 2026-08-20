package com.nowcoder.community.oss.infrastructure.persistence.dataobject;

import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectStatus;
import com.nowcoder.community.oss.domain.model.OssVisibility;

import java.time.Instant;
import java.util.UUID;

public record OssObjectDataObject(
        UUID objectId,
        String usage,
        String ownerService,
        String ownerDomain,
        String ownerType,
        String ownerId,
        String visibility,
        String status,
        UUID currentVersionId,
        String latestFileName,
        String latestContentType,
        long latestContentLength,
        String latestChecksumSha256,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {

    public static OssObjectDataObject from(OssObject object) {
        return new OssObjectDataObject(
                object.objectId(), object.usage(), object.ownerService(), object.ownerDomain(), object.ownerType(),
                object.ownerId(), object.visibility().name(), object.status().name(), object.currentVersionId(),
                object.latestFileName(), object.latestContentType(), object.latestContentLength(),
                object.latestChecksumSha256(), object.createdBy(), object.createdAt(), object.updatedAt()
        );
    }

    public OssObject toDomain() {
        return new OssObject(
                objectId, usage, ownerService, ownerDomain, ownerType, ownerId,
                OssVisibility.valueOf(visibility), OssObjectStatus.valueOf(status), currentVersionId,
                latestFileName, latestContentType, latestContentLength, latestChecksumSha256,
                createdBy, createdAt, updatedAt
        );
    }
}
