package com.nowcoder.community.oss.infrastructure.persistence.dataobject;

import com.nowcoder.community.oss.domain.model.OssAccessGrant;

import java.time.Instant;
import java.util.UUID;

public class OssAccessGrantDataObject {

    private UUID grantId;
    private UUID objectId;
    private UUID versionId;
    private String principalType;
    private String principalValue;
    private String permission;
    private Instant expiresAt;
    private String createdBy;
    private Instant createdAt;
    private Instant revokedAt;

    public static OssAccessGrantDataObject from(OssAccessGrant grant) {
        OssAccessGrantDataObject row = new OssAccessGrantDataObject();
        row.setGrantId(grant.grantId());
        row.setObjectId(grant.objectId());
        row.setVersionId(grant.versionId());
        row.setPrincipalType(grant.principalType());
        row.setPrincipalValue(grant.principalValue());
        row.setPermission(grant.permission());
        row.setExpiresAt(grant.expiresAt());
        row.setCreatedBy(grant.createdBy());
        row.setCreatedAt(grant.createdAt());
        row.setRevokedAt(grant.revokedAt());
        return row;
    }

    public OssAccessGrant toDomain() {
        return new OssAccessGrant(
                grantId,
                objectId,
                versionId,
                principalType,
                principalValue,
                permission,
                expiresAt,
                createdBy,
                createdAt,
                revokedAt
        );
    }

    public UUID getGrantId() { return grantId; }
    public void setGrantId(UUID grantId) { this.grantId = grantId; }
    public UUID getObjectId() { return objectId; }
    public void setObjectId(UUID objectId) { this.objectId = objectId; }
    public UUID getVersionId() { return versionId; }
    public void setVersionId(UUID versionId) { this.versionId = versionId; }
    public String getPrincipalType() { return principalType; }
    public void setPrincipalType(String principalType) { this.principalType = principalType; }
    public String getPrincipalValue() { return principalValue; }
    public void setPrincipalValue(String principalValue) { this.principalValue = principalValue; }
    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}
