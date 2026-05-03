package com.nowcoder.community.im.gateway.shard;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32;

@Component
public class RendezvousWorkerSelector {

    private final WorkerRegistry workerRegistry;

    public RendezvousWorkerSelector(WorkerRegistry workerRegistry) {
        this.workerRegistry = workerRegistry;
    }

    public WorkerDescriptor select(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        List<WorkerDescriptor> candidates;
        try {
            candidates = workerRegistry.healthyWorkers();
        } catch (DuplicateWorkerIdException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "duplicate websocket worker id",
                    ex
            );
        } catch (WorkerDiscoveryException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "websocket worker discovery unavailable",
                    ex
            );
        }
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "no websocket workers available");
        }
        return candidates.stream()
                .max((left, right) -> {
                    int byScore = Long.compare(score(userId, left.getId()), score(userId, right.getId()));
                    if (byScore != 0) {
                        return byScore;
                    }
                    return left.getId().compareTo(right.getId());
                })
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "no websocket workers available"
                ));
    }

    private static long score(UUID userId, String workerId) {
        CRC32 crc32 = new CRC32();
        byte[] bytes = (String.valueOf(userId) + "|" + workerId).getBytes(StandardCharsets.UTF_8);
        crc32.update(bytes, 0, bytes.length);
        return crc32.getValue();
    }
}
