package com.nowcoder.community.im.gateway.shard;

import java.net.URI;
import java.util.Objects;

public class WorkerDescriptor {

    private final String id;
    private final URI uri;

    public WorkerDescriptor(String id, URI uri) {
        this.id = id;
        this.uri = uri;
    }

    public String getId() {
        return id;
    }

    public URI getUri() {
        return uri;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkerDescriptor that)) {
            return false;
        }
        return Objects.equals(id, that.id) && Objects.equals(uri, that.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, uri);
    }
}
