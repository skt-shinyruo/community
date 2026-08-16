package com.nowcoder.community.im.gateway.shard;

import java.net.URI;

public record WorkerDescriptor(
        String id,
        URI uri,
        boolean draining,
        int maxConnections,
        int activeConnectionHint
) {

    public WorkerDescriptor {
        maxConnections = Math.max(maxConnections, 0);
        activeConnectionHint = Math.max(activeConnectionHint, 0);
    }

    public WorkerDescriptor(String id, URI uri) {
        this(id, uri, false, 0, 0);
    }

    public int availableCapacity() {
        return maxConnections <= 0 ? Integer.MAX_VALUE : Math.max(maxConnections - activeConnectionHint, 0);
    }
}
