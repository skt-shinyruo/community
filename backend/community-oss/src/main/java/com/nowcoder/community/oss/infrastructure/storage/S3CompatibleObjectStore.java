package com.nowcoder.community.oss.infrastructure.storage;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class S3CompatibleObjectStore implements ObjectStore {

    private final S3Client s3Client;
    private final S3Presigner presigner;

    public S3CompatibleObjectStore(S3Client s3Client, S3Presigner presigner) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client");
        this.presigner = Objects.requireNonNull(presigner, "presigner");
    }

    @Override
    public void put(String bucket, String key, InputStream content, long contentLength, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(normalizeContentType(contentType))
                .contentLength(contentLength)
                .build();
        s3Client.putObject(request, RequestBody.fromInputStream(content, contentLength));
    }

    @Override
    public Optional<ObjectStoreObject> head(String bucket, String key) {
        try {
            var response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return Optional.of(new ObjectStoreObject(
                    bucket,
                    key,
                    response.contentType(),
                    response.contentLength(),
                    response.eTag(),
                    response.lastModified()
            ));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public StoredObject get(String bucket, String key) {
        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
        return new StoredObject(response, response.response().contentType(), response.response().contentLength());
    }

    @Override
    public void delete(String bucket, String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    @Override
    public PresignedObjectUrl presignUpload(String bucket, String key, Duration ttl, String contentType) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(normalizeContentType(contentType))
                .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(normalizeTtl(ttl))
                .putObjectRequest(putRequest)
                .build());
        return new PresignedObjectUrl(
                presigned.url().toString(),
                presigned.httpRequest().method().name(),
                presigned.expiration(),
                Map.of("Content-Type", normalizeContentType(contentType))
        );
    }

    @Override
    public PresignedObjectUrl presignDownload(String bucket, String key, Duration ttl) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        PresignedGetObjectRequest presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(normalizeTtl(ttl))
                .getObjectRequest(getRequest)
                .build());
        return new PresignedObjectUrl(
                presigned.url().toString(),
                presigned.httpRequest().method().name(),
                presigned.expiration(),
                Map.of()
        );
    }

    private Duration normalizeTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return Duration.ofMinutes(5);
        }
        return ttl;
    }

    private String normalizeContentType(String value) {
        return value == null || value.isBlank() ? "application/octet-stream" : value.trim();
    }
}
