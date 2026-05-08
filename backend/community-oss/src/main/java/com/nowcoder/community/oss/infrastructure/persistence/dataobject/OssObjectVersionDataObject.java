package com.nowcoder.community.oss.infrastructure.persistence.dataobject;

import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssObjectVersionStatus;

import java.time.Instant;
import java.util.UUID;

public class OssObjectVersionDataObject {

    private UUID versionId;
    private UUID objectId;
    private int versionNo;
    private String storageBackend;
    private String storageBucket;
    private String storageKey;
    private String status;
    private String fileName;
    private String contentType;
    private long contentLength;
    private String checksumSha256;
    private String etag;
    private String cacheControl;
    private String contentDisposition;
    private UUID sourceObjectId;
    private String variantType;
    private Instant createdAt;
    private Instant activatedAt;
    private Instant expiredAt;
    private Instant purgedAt;

    public static OssObjectVersionDataObject from(OssObjectVersion version) {
        OssObjectVersionDataObject row = new OssObjectVersionDataObject();
        row.setVersionId(version.versionId());
        row.setObjectId(version.objectId());
        row.setVersionNo(version.versionNo());
        row.setStorageBackend(version.storageBackend());
        row.setStorageBucket(version.storageBucket());
        row.setStorageKey(version.storageKey());
        row.setStatus(version.status().name());
        row.setFileName(version.fileName());
        row.setContentType(version.contentType());
        row.setContentLength(version.contentLength());
        row.setChecksumSha256(version.checksumSha256());
        row.setEtag(version.etag());
        row.setCacheControl(version.cacheControl());
        row.setContentDisposition(version.contentDisposition());
        row.setSourceObjectId(version.sourceObjectId());
        row.setVariantType(version.variantType());
        row.setCreatedAt(version.createdAt());
        row.setActivatedAt(version.activatedAt());
        row.setExpiredAt(version.expiredAt());
        row.setPurgedAt(version.purgedAt());
        return row;
    }

    public OssObjectVersion toDomain() {
        return new OssObjectVersion(
                versionId,
                objectId,
                versionNo,
                storageBackend,
                storageBucket,
                storageKey,
                OssObjectVersionStatus.valueOf(status),
                fileName,
                contentType,
                contentLength,
                checksumSha256,
                etag,
                cacheControl,
                contentDisposition,
                sourceObjectId,
                variantType,
                createdAt,
                activatedAt,
                expiredAt,
                purgedAt
        );
    }

    public UUID getVersionId() { return versionId; }
    public void setVersionId(UUID versionId) { this.versionId = versionId; }
    public UUID getObjectId() { return objectId; }
    public void setObjectId(UUID objectId) { this.objectId = objectId; }
    public int getVersionNo() { return versionNo; }
    public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
    public String getStorageBackend() { return storageBackend; }
    public void setStorageBackend(String storageBackend) { this.storageBackend = storageBackend; }
    public String getStorageBucket() { return storageBucket; }
    public void setStorageBucket(String storageBucket) { this.storageBucket = storageBucket; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getContentLength() { return contentLength; }
    public void setContentLength(long contentLength) { this.contentLength = contentLength; }
    public String getChecksumSha256() { return checksumSha256; }
    public void setChecksumSha256(String checksumSha256) { this.checksumSha256 = checksumSha256; }
    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }
    public String getCacheControl() { return cacheControl; }
    public void setCacheControl(String cacheControl) { this.cacheControl = cacheControl; }
    public String getContentDisposition() { return contentDisposition; }
    public void setContentDisposition(String contentDisposition) { this.contentDisposition = contentDisposition; }
    public UUID getSourceObjectId() { return sourceObjectId; }
    public void setSourceObjectId(UUID sourceObjectId) { this.sourceObjectId = sourceObjectId; }
    public String getVariantType() { return variantType; }
    public void setVariantType(String variantType) { this.variantType = variantType; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getActivatedAt() { return activatedAt; }
    public void setActivatedAt(Instant activatedAt) { this.activatedAt = activatedAt; }
    public Instant getExpiredAt() { return expiredAt; }
    public void setExpiredAt(Instant expiredAt) { this.expiredAt = expiredAt; }
    public Instant getPurgedAt() { return purgedAt; }
    public void setPurgedAt(Instant purgedAt) { this.purgedAt = purgedAt; }
}
