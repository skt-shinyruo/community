package com.nowcoder.community.gateway.shard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

public class ConsistentHashShardRouter implements ShardRouter {

    private final WorkerRegistry registry;

    public ConsistentHashShardRouter(WorkerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Optional<WorkerDescriptor> route(String userId) {
        List<WorkerDescriptor> healthyWorkers = registry.healthyWorkers();
        if (healthyWorkers.isEmpty()) {
            return Optional.empty();
        }
        WorkerDescriptor best = null;
        long bestScore = Long.MIN_VALUE;
        for (WorkerDescriptor worker : healthyWorkers) {
            long score = rendezvousScore(userId, worker.getId());
            if (best == null || Long.compareUnsigned(score, bestScore) > 0) {
                best = worker;
                bestScore = score;
            }
        }
        return Optional.ofNullable(best);
    }

    private static long rendezvousScore(String userId, String workerId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((String.valueOf(userId) + "#" + String.valueOf(workerId)).getBytes(StandardCharsets.UTF_8));
            long value = 0L;
            for (int i = 0; i < Long.BYTES; i++) {
                value = (value << 8) | (bytes[i] & 0xffL);
            }
            return value;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
