package com.nowcoder.community.oss.infrastructure.persistence.dataobject;

import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssObjectVersionStatus;

import java.time.Instant;
import java.util.UUID;

public record OssObjectVersionDataObject(
        UUID versionId,
        UUID objectId,
        int versionNo,
        String storageBackend,
        String storageBucket,
        String storageKey,
        String status,
        String fileName,
        String contentType,
        long contentLength,
        String checksumSha256,
        String etag,
        String cacheControl,
        String contentDisposition,
        UUID sourceObjectId,
        String variantType,
        Instant createdAt,
        Instant activatedAt,
        Instant expiredAt,
        Instant purgedAt
) {

    public static OssObjectVersionDataObject from(OssObjectVersion version) {
        return new OssObjectVersionDataObject(
                version.versionId(), version.objectId(), version.versionNo(), version.storageBackend(),
                version.storageBucket(), version.storageKey(), version.status().name(), version.fileName(),
                version.contentType(), version.contentLength(), version.checksumSha256(), version.etag(),
                version.cacheControl(), version.contentDisposition(), version.sourceObjectId(), version.variantType(),
                version.createdAt(), version.activatedAt(), version.expiredAt(), version.purgedAt()
        );
    }

    public OssObjectVersion toDomain() {
        return new OssObjectVersion(
                versionId, objectId, versionNo, storageBackend, storageBucket, storageKey,
                OssObjectVersionStatus.valueOf(status), fileName, contentType, contentLength,
                checksumSha256, etag, cacheControl, contentDisposition, sourceObjectId, variantType,
                createdAt, activatedAt, expiredAt, purgedAt
        );
    }
}
