package com.nowcoder.community.oss.infrastructure.persistence.dataobject;

import com.nowcoder.community.oss.domain.model.OssAccessGrant;

import java.time.Instant;
import java.util.UUID;

public record OssAccessGrantDataObject(
        UUID grantId,
        UUID objectId,
        UUID versionId,
        String principalType,
        String principalValue,
        String permission,
        Instant expiresAt,
        String createdBy,
        Instant createdAt,
        Instant revokedAt
) {

    public static OssAccessGrantDataObject from(OssAccessGrant grant) {
        return new OssAccessGrantDataObject(
                grant.grantId(), grant.objectId(), grant.versionId(), grant.principalType(),
                grant.principalValue(), grant.permission(), grant.expiresAt(), grant.createdBy(),
                grant.createdAt(), grant.revokedAt()
        );
    }

    public OssAccessGrant toDomain() {
        return new OssAccessGrant(
                grantId, objectId, versionId, principalType, principalValue, permission,
                expiresAt, createdBy, createdAt, revokedAt
        );
    }
}
