package com.nowcoder.community.oss.infrastructure.storage;

import com.nowcoder.community.common.observability.oss.OssRuntimeLogger;

import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

public class ObservedObjectStore implements ObjectStore {

    private static final String ERROR_CODE = "OBJECT_STORE_ERROR";

    private final ObjectStore delegate;
    private final OssRuntimeLogger logger;

    public ObservedObjectStore(ObjectStore delegate, OssRuntimeLogger logger) {
        this.delegate = delegate;
        this.logger = logger;
    }

    @Override
    public void put(String bucket, String key, InputStream content, long contentLength, String contentType) {
        long startedAtNanos = System.nanoTime();
        try {
            delegate.put(bucket, key, content, contentLength, contentType);
            logger.logSlowOperation("upload", bucket, key, contentLength, elapsedMillis(startedAtNanos));
        } catch (RuntimeException ex) {
            logger.logClientError("upload", bucket, key, ERROR_CODE, ex);
            throw ex;
        }
    }

    @Override
    public Optional<ObjectStoreObject> head(String bucket, String key) {
        long startedAtNanos = System.nanoTime();
        try {
            Optional<ObjectStoreObject> object = delegate.head(bucket, key);
            logger.logSlowOperation("download", bucket, key, object.map(ObjectStoreObject::contentLength).orElse(-1L), elapsedMillis(startedAtNanos));
            return object;
        } catch (RuntimeException ex) {
            logger.logClientError("head", bucket, key, ERROR_CODE, ex);
            throw ex;
        }
    }

    @Override
    public StoredObject get(String bucket, String key) {
        long startedAtNanos = System.nanoTime();
        try {
            StoredObject object = delegate.get(bucket, key);
            logger.logSlowOperation("download", bucket, key, object.contentLength(), elapsedMillis(startedAtNanos));
            return object;
        } catch (RuntimeException ex) {
            logger.logClientError("download", bucket, key, ERROR_CODE, ex);
            throw ex;
        }
    }

    @Override
    public void delete(String bucket, String key) {
        try {
            delegate.delete(bucket, key);
        } catch (RuntimeException ex) {
            logger.logClientError("delete", bucket, key, ERROR_CODE, ex);
            throw ex;
        }
    }

    @Override
    public PresignedObjectUrl presignUpload(String bucket, String key, Duration ttl, String contentType) {
        long startedAtNanos = System.nanoTime();
        try {
            PresignedObjectUrl url = delegate.presignUpload(bucket, key, ttl, contentType);
            logger.logSlowOperation("upload", bucket, key, -1, elapsedMillis(startedAtNanos));
            return url;
        } catch (RuntimeException ex) {
            logger.logClientError("presign_upload", bucket, key, ERROR_CODE, ex);
            throw ex;
        }
    }

    @Override
    public PresignedObjectUrl presignDownload(String bucket, String key, Duration ttl) {
        long startedAtNanos = System.nanoTime();
        try {
            PresignedObjectUrl url = delegate.presignDownload(bucket, key, ttl);
            logger.logSlowOperation("download", bucket, key, -1, elapsedMillis(startedAtNanos));
            return url;
        } catch (RuntimeException ex) {
            logger.logClientError("presign_download", bucket, key, ERROR_CODE, ex);
            throw ex;
        }
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
