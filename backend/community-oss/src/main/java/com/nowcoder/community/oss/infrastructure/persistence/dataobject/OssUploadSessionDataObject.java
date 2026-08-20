package com.nowcoder.community.oss.infrastructure.persistence.dataobject;

import com.nowcoder.community.oss.domain.model.OssUploadSession;
import com.nowcoder.community.oss.domain.model.OssUploadSessionStatus;

import java.time.Instant;
import java.util.UUID;

public record OssUploadSessionDataObject(
        UUID sessionId,
        UUID requestId,
        UUID objectId,
        UUID versionId,
        String uploadMode,
        String ownerService,
        String ownerDomain,
        String ownerType,
        String ownerId,
        String expectedFileName,
        String expectedContentType,
        long expectedContentLength,
        String expectedChecksumSha256,
        String status,
        long claimVersion,
        Instant expiresAt,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        String lastError
) {

    public static OssUploadSessionDataObject from(OssUploadSession session) {
        return new OssUploadSessionDataObject(
                session.sessionId(), session.requestId(), session.objectId(), session.versionId(), session.uploadMode(),
                session.ownerService(), session.ownerDomain(), session.ownerType(), session.ownerId(),
                session.expectedFileName(), session.expectedContentType(), session.expectedContentLength(),
                session.expectedChecksumSha256(), session.status().name(), session.claimVersion(), session.expiresAt(),
                session.createdBy(), session.createdAt(), session.updatedAt(), session.completedAt(), session.lastError()
        );
    }

    public UUID requestId() {
        return requestId == null ? sessionId : requestId;
    }

    public Instant updatedAt() {
        return updatedAt == null ? createdAt : updatedAt;
    }

    public String lastError() {
        return lastError == null ? "" : lastError;
    }

    public OssUploadSession toDomain() {
        return new OssUploadSession(
                sessionId, requestId, objectId, versionId, uploadMode, ownerService, ownerDomain, ownerType, ownerId,
                expectedFileName, expectedContentType, expectedContentLength, expectedChecksumSha256,
                OssUploadSessionStatus.valueOf(status), claimVersion, expiresAt, createdBy, createdAt,
                updatedAt, completedAt, lastError
        );
    }
}
