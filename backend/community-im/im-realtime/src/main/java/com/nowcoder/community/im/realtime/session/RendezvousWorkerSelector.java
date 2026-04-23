package com.nowcoder.community.im.realtime.session;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.CRC32;

@Component
public class RendezvousWorkerSelector {

    private final DiscoveryClient discoveryClient;
    private final ImSessionProperties properties;

    public RendezvousWorkerSelector(DiscoveryClient discoveryClient, ImSessionProperties properties) {
        this.discoveryClient = discoveryClient;
        this.properties = properties;
    }

    public SelectedWorker select(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        List<SelectedWorker> candidates = discoveredWorkers();
        if (candidates.isEmpty()) {
            candidates = fallbackWorkers();
        }
        return candidates.stream()
                .max((left, right) -> {
                    int byScore = Long.compare(score(userId, left.workerId()), score(userId, right.workerId()));
                    if (byScore != 0) {
                        return byScore;
                    }
                    return left.workerId().compareTo(right.workerId());
                })
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "no websocket workers available"
                ));
    }

    private List<SelectedWorker> discoveredWorkers() {
        if (discoveryClient == null || !StringUtils.hasText(properties.getWorkerServiceId())) {
            return List.of();
        }
        Map<String, SelectedWorker> deduped = new LinkedHashMap<>();
        for (ServiceInstance instance : discoveryClient.getInstances(properties.getWorkerServiceId())) {
            Optional<SelectedWorker> candidate = fromInstance(instance);
            if (candidate.isEmpty()) {
                continue;
            }
            SelectedWorker previous = deduped.putIfAbsent(candidate.get().workerId(), candidate.get());
            if (previous != null) {
                throw new IllegalStateException("duplicate websocket worker id: " + candidate.get().workerId());
            }
        }
        return List.copyOf(deduped.values());
    }

    private List<SelectedWorker> fallbackWorkers() {
        if (!StringUtils.hasText(properties.getWorkerId()) || !StringUtils.hasText(properties.getWsBaseUrl())) {
            return List.of();
        }
        return List.of(new SelectedWorker(properties.getWorkerId().trim(), properties.getWsBaseUrl().trim()));
    }

    private Optional<SelectedWorker> fromInstance(ServiceInstance instance) {
        if (instance == null) {
            return Optional.empty();
        }
        Map<String, String> metadata = instance.getMetadata();
        String workerId = metadata.get(properties.getWorkerIdMetadataKey());
        String wsPath = metadata.get(properties.getWsPathMetadataKey());
        String wsPort = metadata.get(properties.getWsPortMetadataKey());
        if (!StringUtils.hasText(workerId) || !StringUtils.hasText(wsPath) || !StringUtils.hasText(wsPort)) {
            return Optional.empty();
        }
        String normalizedPath = wsPath.startsWith("/") ? wsPath.trim() : "/" + wsPath.trim();
        String scheme = instance.isSecure() ? "wss" : "ws";
        String wsUrl = scheme + "://" + instance.getHost() + ":" + wsPort.trim() + normalizedPath;
        return Optional.of(new SelectedWorker(workerId.trim(), wsUrl));
    }

    private static long score(UUID userId, String workerId) {
        CRC32 crc32 = new CRC32();
        byte[] bytes = (String.valueOf(userId) + "|" + workerId).getBytes(StandardCharsets.UTF_8);
        crc32.update(bytes, 0, bytes.length);
        return crc32.getValue();
    }

    public record SelectedWorker(String workerId, String wsUrl) {
    }
}
