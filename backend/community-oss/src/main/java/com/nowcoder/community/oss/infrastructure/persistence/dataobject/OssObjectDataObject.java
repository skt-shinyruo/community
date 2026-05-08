package com.nowcoder.community.oss.infrastructure.persistence.dataobject;

import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectStatus;
import com.nowcoder.community.oss.domain.model.OssVisibility;

import java.time.Instant;
import java.util.UUID;

public class OssObjectDataObject {

    private UUID objectId;
    private String usage;
    private String ownerService;
    private String ownerDomain;
    private String ownerType;
    private String ownerId;
    private String visibility;
    private String status;
    private UUID currentVersionId;
    private String latestFileName;
    private String latestContentType;
    private long latestContentLength;
    private String latestChecksumSha256;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public static OssObjectDataObject from(OssObject object) {
        OssObjectDataObject row = new OssObjectDataObject();
        row.setObjectId(object.objectId());
        row.setUsage(object.usage());
        row.setOwnerService(object.ownerService());
        row.setOwnerDomain(object.ownerDomain());
        row.setOwnerType(object.ownerType());
        row.setOwnerId(object.ownerId());
        row.setVisibility(object.visibility().name());
        row.setStatus(object.status().name());
        row.setCurrentVersionId(object.currentVersionId());
        row.setLatestFileName(object.latestFileName());
        row.setLatestContentType(object.latestContentType());
        row.setLatestContentLength(object.latestContentLength());
        row.setLatestChecksumSha256(object.latestChecksumSha256());
        row.setCreatedBy(object.createdBy());
        row.setCreatedAt(object.createdAt());
        row.setUpdatedAt(object.updatedAt());
        return row;
    }

    public OssObject toDomain() {
        return new OssObject(
                objectId,
                usage,
                ownerService,
                ownerDomain,
                ownerType,
                ownerId,
                OssVisibility.valueOf(visibility),
                OssObjectStatus.valueOf(status),
                currentVersionId,
                latestFileName,
                latestContentType,
                latestContentLength,
                latestChecksumSha256,
                createdBy,
                createdAt,
                updatedAt
        );
    }

    public UUID getObjectId() {
        return objectId;
    }

    public void setObjectId(UUID objectId) {
        this.objectId = objectId;
    }

    public String getUsage() {
        return usage;
    }

    public void setUsage(String usage) {
        this.usage = usage;
    }

    public String getOwnerService() {
        return ownerService;
    }

    public void setOwnerService(String ownerService) {
        this.ownerService = ownerService;
    }

    public String getOwnerDomain() {
        return ownerDomain;
    }

    public void setOwnerDomain(String ownerDomain) {
        this.ownerDomain = ownerDomain;
    }

    public String getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(String ownerType) {
        this.ownerType = ownerType;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getCurrentVersionId() {
        return currentVersionId;
    }

    public void setCurrentVersionId(UUID currentVersionId) {
        this.currentVersionId = currentVersionId;
    }

    public String getLatestFileName() {
        return latestFileName;
    }

    public void setLatestFileName(String latestFileName) {
        this.latestFileName = latestFileName;
    }

    public String getLatestContentType() {
        return latestContentType;
    }

    public void setLatestContentType(String latestContentType) {
        this.latestContentType = latestContentType;
    }

    public long getLatestContentLength() {
        return latestContentLength;
    }

    public void setLatestContentLength(long latestContentLength) {
        this.latestContentLength = latestContentLength;
    }

    public String getLatestChecksumSha256() {
        return latestChecksumSha256;
    }

    public void setLatestChecksumSha256(String latestChecksumSha256) {
        this.latestChecksumSha256 = latestChecksumSha256;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
