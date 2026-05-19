package com.nowcoder.community.im.realtime.presence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class RedisRoomPresenceDirectory implements RoomPresenceDirectory {

    private static final Logger log = LoggerFactory.getLogger(RedisRoomPresenceDirectory.class);

    private final StringRedisTemplate redisTemplate;
    private final RoomPresenceProperties properties;

    public RedisRoomPresenceDirectory(StringRedisTemplate redisTemplate, RoomPresenceProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties == null ? new RoomPresenceProperties() : properties;
    }

    @Override
    public void activate(UUID roomId, String workerId) {
        String normalizedWorkerId = normalizeWorkerId(workerId);
        if (roomId == null || normalizedWorkerId == null) {
            return;
        }
        redisTemplate.opsForSet().add(roomWorkersKey(roomId), normalizedWorkerId);
        redisTemplate.opsForValue().set(workerLivenessKey(roomId, normalizedWorkerId), "1", ttl());
    }

    @Override
    public void deactivate(UUID roomId, String workerId) {
        String normalizedWorkerId = normalizeWorkerId(workerId);
        if (roomId == null || normalizedWorkerId == null) {
            return;
        }
        redisTemplate.opsForSet().remove(roomWorkersKey(roomId), normalizedWorkerId);
        redisTemplate.delete(workerLivenessKey(roomId, normalizedWorkerId));
    }

    @Override
    public Set<String> activeWorkerIds(UUID roomId) {
        if (roomId == null) {
            return Set.of();
        }
        String roomWorkersKey = roomWorkersKey(roomId);
        Set<String> workerIds = redisTemplate.opsForSet().members(roomWorkersKey);
        if (workerIds == null || workerIds.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> active = new LinkedHashSet<>();
        for (String workerId : workerIds) {
            String normalizedWorkerId = normalizeWorkerId(workerId);
            if (normalizedWorkerId == null) {
                continue;
            }
            if (Boolean.TRUE.equals(redisTemplate.hasKey(workerLivenessKey(roomId, normalizedWorkerId)))) {
                active.add(normalizedWorkerId);
            } else {
                removeStaleMember(roomWorkersKey, normalizedWorkerId);
            }
        }
        return Set.copyOf(active);
    }

    private void removeStaleMember(String roomWorkersKey, String workerId) {
        try {
            redisTemplate.opsForSet().remove(roomWorkersKey, workerId);
        } catch (RuntimeException ex) {
            log.debug("Failed to remove stale room presence member: key={}, workerId={}", roomWorkersKey, workerId);
        }
    }

    private Duration ttl() {
        return properties.normalizedTtl();
    }

    private String roomWorkersKey(UUID roomId) {
        return properties.normalizedKeyPrefix() + "room:" + roomId + ":workers";
    }

    private String workerLivenessKey(UUID roomId, String workerId) {
        return properties.normalizedKeyPrefix() + "room:" + roomId + ":worker:" + workerId;
    }

    private static String normalizeWorkerId(String workerId) {
        if (!StringUtils.hasText(workerId)) {
            return null;
        }
        return workerId.trim();
    }
}
