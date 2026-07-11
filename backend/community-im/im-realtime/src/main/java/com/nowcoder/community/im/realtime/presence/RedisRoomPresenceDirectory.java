package com.nowcoder.community.im.realtime.presence;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class RedisRoomPresenceDirectory implements RoomPresenceDirectory {

    private final StringRedisTemplate redisTemplate;
    private final RoomPresenceProperties properties;
    private final Clock clock;

    public RedisRoomPresenceDirectory(StringRedisTemplate redisTemplate, RoomPresenceProperties properties) {
        this(redisTemplate, properties, Clock.systemUTC());
    }

    RedisRoomPresenceDirectory(
            StringRedisTemplate redisTemplate,
            RoomPresenceProperties properties,
            Clock clock
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties == null ? new RoomPresenceProperties() : properties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public void activate(UUID roomId, String workerId) {
        String normalizedWorkerId = normalizeWorkerId(workerId);
        if (roomId == null || normalizedWorkerId == null) {
            return;
        }
        long expiresAtEpochMillis = Math.addExact(clock.millis(), ttl().toMillis());
        zSetOperations().add(roomWorkersKey(roomId), normalizedWorkerId, expiresAtEpochMillis);
    }

    @Override
    public void deactivate(UUID roomId, String workerId) {
        String normalizedWorkerId = normalizeWorkerId(workerId);
        if (roomId == null || normalizedWorkerId == null) {
            return;
        }
        zSetOperations().remove(roomWorkersKey(roomId), normalizedWorkerId);
    }

    @Override
    public Set<String> activeWorkerIds(UUID roomId) {
        if (roomId == null) {
            return Set.of();
        }
        String key = roomWorkersKey(roomId);
        long nowEpochMillis = clock.millis();
        ZSetOperations<String, String> operations = zSetOperations();
        operations.removeRangeByScore(key, Double.NEGATIVE_INFINITY, nowEpochMillis);
        Set<String> workerIds = operations.rangeByScore(key, nowEpochMillis, Double.POSITIVE_INFINITY);
        if (workerIds == null || workerIds.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> active = new LinkedHashSet<>();
        for (String workerId : workerIds) {
            String normalizedWorkerId = normalizeWorkerId(workerId);
            if (normalizedWorkerId == null) {
                continue;
            }
            active.add(normalizedWorkerId);
        }
        return Set.copyOf(active);
    }

    private ZSetOperations<String, String> zSetOperations() {
        return redisTemplate.opsForZSet();
    }

    private Duration ttl() {
        return properties.normalizedTtl();
    }

    private String roomWorkersKey(UUID roomId) {
        return properties.normalizedKeyPrefix() + "room:" + roomId + ":workers";
    }

    private static String normalizeWorkerId(String workerId) {
        if (!StringUtils.hasText(workerId)) {
            return null;
        }
        return workerId.trim();
    }
}
