package com.nowcoder.community.gateway.shard;

import java.net.URI;

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
}
