package com.nowcoder.community.gateway.http;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class UpstreamPool {

    private final String routeId;
    private final List<URI> upstreams;
    private final AtomicInteger cursor = new AtomicInteger(0);

    private UpstreamPool(String routeId, List<URI> upstreams) {
        this.routeId = routeId;
        this.upstreams = List.copyOf(upstreams);
    }

    public static UpstreamPool from(UpstreamRouteProperties.Route route) {
        if (route == null) {
            throw new IllegalArgumentException("route is required");
        }
        Set<URI> normalized = new LinkedHashSet<>();
        if (route.getUri() != null) {
            normalized.add(route.getUri());
        }
        if (route.getUris() != null) {
            for (URI uri : route.getUris()) {
                if (uri != null) {
                    normalized.add(uri);
                }
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("route must define at least one upstream");
        }
        return new UpstreamPool(route.getId(), new ArrayList<>(normalized));
    }

    public String routeId() {
        return routeId;
    }

    public List<URI> upstreams() {
        return upstreams;
    }

    public List<URI> nextCandidates() {
        if (upstreams.isEmpty()) {
            return List.of();
        }
        int size = upstreams.size();
        int start = Math.floorMod(cursor.getAndIncrement(), size);
        ArrayList<URI> ordered = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ordered.add(upstreams.get((start + i) % size));
        }
        return List.copyOf(ordered);
    }
}
