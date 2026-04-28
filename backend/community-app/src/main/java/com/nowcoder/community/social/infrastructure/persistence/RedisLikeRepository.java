package com.nowcoder.community.social.infrastructure.persistence;

import com.nowcoder.community.social.domain.repository.LikeRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "social.storage", havingValue = "redis")
public class RedisLikeRepository implements LikeRepository {

    private static final DefaultRedisScript<Long> SET_LIKE_SCRIPT = new DefaultRedisScript<>(
            """
            local entityKey = KEYS[1]
            local userKey = KEYS[2]

            local actorUserId = ARGV[1]
            local liked = ARGV[2]

            if liked == '1' then
              local added = redis.call('SADD', entityKey, actorUserId)
              if added and added > 0 then
                redis.call('INCRBY', userKey, 1)
                return 1
              end
              return 0
            end

            local removed = redis.call('SREM', entityKey, actorUserId)
            if removed and removed > 0 then
              local v = redis.call('INCRBY', userKey, -1)
              if v and v < 0 then
                redis.call('SET', userKey, 0)
              end
              return 1
            end
            return 0
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public RedisLikeRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean addLike(UUID userId, int entityType, UUID entityId) {
        Long added = redisTemplate.opsForSet().add(entityKey(entityType, entityId), String.valueOf(userId));
        return added != null && added > 0;
    }

    @Override
    public boolean removeLike(UUID userId, int entityType, UUID entityId) {
        Long removed = redisTemplate.opsForSet().remove(entityKey(entityType, entityId), String.valueOf(userId));
        return removed != null && removed > 0;
    }

    @Override
    public boolean isLiked(UUID userId, int entityType, UUID entityId) {
        Boolean member = redisTemplate.opsForSet().isMember(entityKey(entityType, entityId), String.valueOf(userId));
        return member != null && member;
    }

    @Override
    public long countEntityLikes(int entityType, UUID entityId) {
        Long size = redisTemplate.opsForSet().size(entityKey(entityType, entityId));
        return size == null ? 0 : size;
    }

    @Override
    public long incrementUserLikeCount(UUID userId, long delta) {
        Long v = redisTemplate.opsForValue().increment(userKey(userId), delta);
        return v == null ? 0 : v;
    }

    @Override
    public long getUserLikeCount(UUID userId) {
        String v = redisTemplate.opsForValue().get(userKey(userId));
        if (v == null || v.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Long.parseLong(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public boolean requiresExplicitCompensation() {
        return true;
    }

    @Override
    public boolean setLike(UUID actorUserId, int entityType, UUID entityId, UUID entityUserId, boolean liked) {
        // entityUserId 为空时不更新获赞计数（保持与默认实现一致）
        if (entityUserId == null) {
            return LikeRepository.super.setLike(actorUserId, entityType, entityId, entityUserId, liked);
        }
        String entityKey = entityKey(entityType, entityId);
        String userKey = userKey(entityUserId);
        Long changed = redisTemplate.execute(
                SET_LIKE_SCRIPT,
                List.of(entityKey, userKey),
                String.valueOf(actorUserId),
                liked ? "1" : "0"
        );
        return changed != null && changed > 0;
    }

    private String entityKey(int entityType, UUID entityId) {
        return "like:entity:" + entityType + ":" + entityId;
    }

    private String userKey(UUID userId) {
        return "like:user:" + userId;
    }
}
