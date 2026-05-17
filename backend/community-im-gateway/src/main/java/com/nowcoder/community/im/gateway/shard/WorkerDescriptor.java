package com.nowcoder.community.im.gateway.shard;

import java.net.URI;
import java.util.Objects;

public class WorkerDescriptor {

    private final String id;
    private final URI uri;
    private final boolean draining;
    private final int maxConnections;
    private final int activeConnectionHint;
    private final String shardGroup;

    public WorkerDescriptor(String id, URI uri) {
        this(id, uri, false, 0, 0, "default");
    }

    public WorkerDescriptor(String id, URI uri, boolean draining, int maxConnections,
                            int activeConnectionHint, String shardGroup) {
        this.id = id;
        this.uri = uri;
        this.draining = draining;
        this.maxConnections = Math.max(maxConnections, 0);
        this.activeConnectionHint = Math.max(activeConnectionHint, 0);
        this.shardGroup = shardGroup == null || shardGroup.isBlank() ? "default" : shardGroup.trim();
    }

    public String getId() {
        return id;
    }

    public URI getUri() {
        return uri;
    }

    public boolean isDraining() {
        return draining;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public int getActiveConnectionHint() {
        return activeConnectionHint;
    }

    public String getShardGroup() {
        return shardGroup;
    }

    public int availableCapacity() {
        if (maxConnections <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(maxConnections - activeConnectionHint, 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkerDescriptor that)) {
            return false;
        }
        return draining == that.draining
                && maxConnections == that.maxConnections
                && activeConnectionHint == that.activeConnectionHint
                && Objects.equals(id, that.id)
                && Objects.equals(uri, that.uri)
                && Objects.equals(shardGroup, that.shardGroup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, uri, draining, maxConnections, activeConnectionHint, shardGroup);
    }
}
