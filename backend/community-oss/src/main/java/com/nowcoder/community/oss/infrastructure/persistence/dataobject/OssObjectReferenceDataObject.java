package com.nowcoder.community.oss.infrastructure.persistence.dataobject;

import com.nowcoder.community.oss.domain.model.OssObjectReference;
import com.nowcoder.community.oss.domain.model.OssObjectReferenceStatus;

import java.time.Instant;
import java.util.UUID;

public class OssObjectReferenceDataObject {

    private UUID referenceId;
    private UUID objectId;
    private UUID versionId;
    private String subjectService;
    private String subjectDomain;
    private String subjectType;
    private String subjectId;
    private String referenceRole;
    private String status;
    private Instant retainUntil;
    private Instant createdAt;
    private Instant releasedAt;

    public static OssObjectReferenceDataObject from(OssObjectReference reference) {
        OssObjectReferenceDataObject row = new OssObjectReferenceDataObject();
        row.setReferenceId(reference.referenceId());
        row.setObjectId(reference.objectId());
        row.setVersionId(reference.versionId());
        row.setSubjectService(reference.subjectService());
        row.setSubjectDomain(reference.subjectDomain());
        row.setSubjectType(reference.subjectType());
        row.setSubjectId(reference.subjectId());
        row.setReferenceRole(reference.referenceRole());
        row.setStatus(reference.status().name());
        row.setRetainUntil(reference.retainUntil());
        row.setCreatedAt(reference.createdAt());
        row.setReleasedAt(reference.releasedAt());
        return row;
    }

    public OssObjectReference toDomain() {
        return new OssObjectReference(
                referenceId,
                objectId,
                versionId,
                subjectService,
                subjectDomain,
                subjectType,
                subjectId,
                referenceRole,
                OssObjectReferenceStatus.valueOf(status),
                retainUntil,
                createdAt,
                releasedAt
        );
    }

    public UUID getReferenceId() { return referenceId; }
    public void setReferenceId(UUID referenceId) { this.referenceId = referenceId; }
    public UUID getObjectId() { return objectId; }
    public void setObjectId(UUID objectId) { this.objectId = objectId; }
    public UUID getVersionId() { return versionId; }
    public void setVersionId(UUID versionId) { this.versionId = versionId; }
    public String getSubjectService() { return subjectService; }
    public void setSubjectService(String subjectService) { this.subjectService = subjectService; }
    public String getSubjectDomain() { return subjectDomain; }
    public void setSubjectDomain(String subjectDomain) { this.subjectDomain = subjectDomain; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public String getReferenceRole() { return referenceRole; }
    public void setReferenceRole(String referenceRole) { this.referenceRole = referenceRole; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getRetainUntil() { return retainUntil; }
    public void setRetainUntil(Instant retainUntil) { this.retainUntil = retainUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Instant releasedAt) { this.releasedAt = releasedAt; }
}
