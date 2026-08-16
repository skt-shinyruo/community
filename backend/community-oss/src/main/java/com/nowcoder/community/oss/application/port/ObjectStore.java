package com.nowcoder.community.oss.application.port;

import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

public interface ObjectStore extends ObjectDeletePort {

    void put(String bucket, String key, InputStream content, long contentLength, String contentType);

    Optional<ObjectStoreObject> head(String bucket, String key);

    StoredObject get(String bucket, String key);

    void delete(String bucket, String key);

    @Override
    default void deleteIfExists(String bucket, String key) {
        delete(bucket, key);
    }

    PresignedObjectUrl presignUpload(String bucket, String key, Duration ttl, String contentType);

    PresignedObjectUrl presignDownload(String bucket, String key, Duration ttl);
}
