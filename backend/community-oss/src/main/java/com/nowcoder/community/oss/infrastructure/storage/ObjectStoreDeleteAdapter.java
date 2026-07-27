package com.nowcoder.community.oss.infrastructure.storage;

import com.nowcoder.community.oss.application.port.ObjectDeletePort;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ObjectStoreDeleteAdapter implements ObjectDeletePort {

    private final ObjectStore objectStore;

    public ObjectStoreDeleteAdapter(ObjectStore objectStore) {
        this.objectStore = Objects.requireNonNull(objectStore, "objectStore must not be null");
    }

    @Override
    public void deleteIfExists(String bucket, String key) {
        objectStore.delete(bucket, key);
    }
}
