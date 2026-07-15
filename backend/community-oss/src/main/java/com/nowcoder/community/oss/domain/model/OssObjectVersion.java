package com.nowcoder.community.oss.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OssObjectVersion(
        UUID versionId,
        UUID objectId,
        int versionNo,
        String storageBackend,
        String storageBucket,
        String storageKey,
        OssObjectVersionStatus status,
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

    public OssObjectVersion {
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(objectId, "objectId");
        versionNo = Math.max(1, versionNo);
        storageBackend = requireText(storageBackend, "storageBackend");
        storageBucket = requireText(storageBucket, "storageBucket");
        storageKey = requireText(storageKey, "storageKey");
        status = status == null ? OssObjectVersionStatus.STAGED : status;
        fileName = requireText(fileName, "fileName");
        contentType = normalizeContentType(contentType);
        contentLength = Math.max(0, contentLength);
        checksumSha256 = normalize(checksumSha256);
        etag = normalize(etag);
        cacheControl = normalize(cacheControl);
        contentDisposition = normalize(contentDisposition);
        variantType = normalize(variantType);
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static OssObjectVersion staged(
            UUID versionId,
            UUID objectId,
            String storageBackend,
            String storageBucket,
            String storageKey,
            String fileName,
            String contentType,
            long contentLength,
            String checksumSha256,
            Instant now
    ) {
        return new OssObjectVersion(
                versionId,
                objectId,
                1,
                storageBackend,
                storageBucket,
                storageKey,
                OssObjectVersionStatus.STAGED,
                fileName,
                contentType,
                contentLength,
                checksumSha256,
                "",
                "",
                "",
                null,
                "",
                now,
                null,
                null,
                null
        );
    }

    public OssObjectVersion activate(String etag, Instant now) {
        return new OssObjectVersion(
                versionId,
                objectId,
                versionNo,
                storageBackend,
                storageBucket,
                storageKey,
                OssObjectVersionStatus.ACTIVE,
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
                now,
                expiredAt,
                purgedAt
        );
    }

    public OssObjectVersion purge(Instant now) {
        return new OssObjectVersion(
                versionId,
                objectId,
                versionNo,
                storageBackend,
                storageBucket,
                storageKey,
                OssObjectVersionStatus.PURGED,
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
                now
        );
    }

    public OssObjectVersion withUploadedContent(String uploadedContentType, long uploadedContentLength, String uploadedChecksumSha256) {
        return withUploadedContentAt(
                storageKey,
                uploadedContentType,
                uploadedContentLength,
                uploadedChecksumSha256
        );
    }

    public OssObjectVersion withUploadedContentAt(
            String uploadedStorageKey,
            String uploadedContentType,
            long uploadedContentLength,
            String uploadedChecksumSha256
    ) {
        return new OssObjectVersion(
                versionId,
                objectId,
                versionNo,
                storageBackend,
                storageBucket,
                uploadedStorageKey,
                status,
                fileName,
                normalizeContentType(uploadedContentType),
                uploadedContentLength,
                uploadedChecksumSha256,
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

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeContentType(String value) {
        return value == null || value.isBlank() ? "application/octet-stream" : value.trim();
    }
}
