package com.nowcoder.community.oss.infrastructure.storage;

import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

public interface ObjectStore {

    void put(String bucket, String key, InputStream content, long contentLength, String contentType);

    Optional<ObjectStoreObject> head(String bucket, String key);

    StoredObject get(String bucket, String key);

    void delete(String bucket, String key);

    PresignedObjectUrl presignUpload(String bucket, String key, Duration ttl, String contentType);

    PresignedObjectUrl presignDownload(String bucket, String key, Duration ttl);
}
