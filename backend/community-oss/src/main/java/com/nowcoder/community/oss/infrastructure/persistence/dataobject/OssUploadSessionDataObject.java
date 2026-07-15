package com.nowcoder.community.oss.infrastructure.persistence.dataobject;

import com.nowcoder.community.oss.domain.model.OssUploadSession;
import com.nowcoder.community.oss.domain.model.OssUploadSessionStatus;

import java.time.Instant;
import java.util.UUID;

public class OssUploadSessionDataObject {

    private UUID sessionId;
    private UUID requestId;
    private UUID objectId;
    private UUID versionId;
    private String uploadMode;
    private String ownerService;
    private String ownerDomain;
    private String ownerType;
    private String ownerId;
    private String expectedFileName;
    private String expectedContentType;
    private long expectedContentLength;
    private String expectedChecksumSha256;
    private String status;
    private long claimVersion;
    private Instant expiresAt;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
    private String lastError;

    public static OssUploadSessionDataObject from(OssUploadSession session) {
        OssUploadSessionDataObject row = new OssUploadSessionDataObject();
        row.setSessionId(session.sessionId());
        row.setRequestId(session.requestId());
        row.setObjectId(session.objectId());
        row.setVersionId(session.versionId());
        row.setUploadMode(session.uploadMode());
        row.setOwnerService(session.ownerService());
        row.setOwnerDomain(session.ownerDomain());
        row.setOwnerType(session.ownerType());
        row.setOwnerId(session.ownerId());
        row.setExpectedFileName(session.expectedFileName());
        row.setExpectedContentType(session.expectedContentType());
        row.setExpectedContentLength(session.expectedContentLength());
        row.setExpectedChecksumSha256(session.expectedChecksumSha256());
        row.setStatus(session.status().name());
        row.setClaimVersion(session.claimVersion());
        row.setExpiresAt(session.expiresAt());
        row.setCreatedBy(session.createdBy());
        row.setCreatedAt(session.createdAt());
        row.setUpdatedAt(session.updatedAt());
        row.setCompletedAt(session.completedAt());
        row.setLastError(session.lastError());
        return row;
    }

    public OssUploadSession toDomain() {
        return new OssUploadSession(
                sessionId,
                requestId,
                objectId,
                versionId,
                uploadMode,
                ownerService,
                ownerDomain,
                ownerType,
                ownerId,
                expectedFileName,
                expectedContentType,
                expectedContentLength,
                expectedChecksumSha256,
                OssUploadSessionStatus.valueOf(status),
                claimVersion,
                expiresAt,
                createdBy,
                createdAt,
                updatedAt,
                completedAt,
                lastError
        );
    }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public UUID getRequestId() { return requestId == null ? sessionId : requestId; }
    public void setRequestId(UUID requestId) { this.requestId = requestId; }
    public UUID getObjectId() { return objectId; }
    public void setObjectId(UUID objectId) { this.objectId = objectId; }
    public UUID getVersionId() { return versionId; }
    public void setVersionId(UUID versionId) { this.versionId = versionId; }
    public String getUploadMode() { return uploadMode; }
    public void setUploadMode(String uploadMode) { this.uploadMode = uploadMode; }
    public String getOwnerService() { return ownerService; }
    public void setOwnerService(String ownerService) { this.ownerService = ownerService; }
    public String getOwnerDomain() { return ownerDomain; }
    public void setOwnerDomain(String ownerDomain) { this.ownerDomain = ownerDomain; }
    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getExpectedFileName() { return expectedFileName; }
    public void setExpectedFileName(String expectedFileName) { this.expectedFileName = expectedFileName; }
    public String getExpectedContentType() { return expectedContentType; }
    public void setExpectedContentType(String expectedContentType) { this.expectedContentType = expectedContentType; }
    public long getExpectedContentLength() { return expectedContentLength; }
    public void setExpectedContentLength(long expectedContentLength) { this.expectedContentLength = expectedContentLength; }
    public String getExpectedChecksumSha256() { return expectedChecksumSha256; }
    public void setExpectedChecksumSha256(String expectedChecksumSha256) { this.expectedChecksumSha256 = expectedChecksumSha256; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getClaimVersion() { return claimVersion; }
    public void setClaimVersion(long claimVersion) { this.claimVersion = claimVersion; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt == null ? createdAt : updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getLastError() { return lastError == null ? "" : lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
