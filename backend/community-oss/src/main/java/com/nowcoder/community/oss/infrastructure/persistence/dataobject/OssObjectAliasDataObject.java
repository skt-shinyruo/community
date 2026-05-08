package com.nowcoder.community.oss.infrastructure.persistence.dataobject;

import com.nowcoder.community.oss.domain.model.OssObjectAlias;

import java.time.Instant;
import java.util.UUID;

public class OssObjectAliasDataObject {

    private String aliasKey;
    private UUID objectId;
    private UUID versionId;
    private String status;
    private Instant expiresAt;
    private Instant createdAt;

    public static OssObjectAliasDataObject from(OssObjectAlias alias) {
        OssObjectAliasDataObject row = new OssObjectAliasDataObject();
        row.setAliasKey(alias.aliasKey());
        row.setObjectId(alias.objectId());
        row.setVersionId(alias.versionId());
        row.setStatus(alias.status());
        row.setExpiresAt(alias.expiresAt());
        row.setCreatedAt(alias.createdAt());
        return row;
    }

    public OssObjectAlias toDomain() {
        return new OssObjectAlias(aliasKey, objectId, versionId, status, expiresAt, createdAt);
    }

    public String getAliasKey() { return aliasKey; }
    public void setAliasKey(String aliasKey) { this.aliasKey = aliasKey; }
    public UUID getObjectId() { return objectId; }
    public void setObjectId(UUID objectId) { this.objectId = objectId; }
    public UUID getVersionId() { return versionId; }
    public void setVersionId(UUID versionId) { this.versionId = versionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
