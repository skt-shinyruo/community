package com.nowcoder.community.social.infrastructure.persistence;

import com.nowcoder.community.social.domain.model.LikeRelation;
import com.nowcoder.community.social.domain.repository.LikeRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "social.storage", havingValue = "redis")
public class RedisLikeRepository implements LikeRepository {

    private static final DefaultRedisScript<Long> SET_LIKE_SCRIPT = new DefaultRedisScript<>(
            """
            local entityKey = KEYS[1]
            local userKey = KEYS[2]
            local ownerKey = KEYS[3]

            local actorUserId = ARGV[1]
            local liked = ARGV[2]
            local entityUserId = ARGV[3]

            if liked == '1' then
              local added = redis.call('SADD', entityKey, actorUserId)
              if added and added > 0 then
                if entityUserId and entityUserId ~= '' then
                  redis.call('HSET', ownerKey, actorUserId, entityUserId)
                end
                redis.call('INCRBY', userKey, 1)
                return 1
              end
              return 0
            end

            if (not entityUserId) or entityUserId == '' then
              entityUserId = redis.call('HGET', ownerKey, actorUserId)
            end
            local removed = redis.call('SREM', entityKey, actorUserId)
            if removed and removed > 0 then
              redis.call('HDEL', ownerKey, actorUserId)
              if entityUserId and entityUserId ~= '' then
                local v = redis.call('INCRBY', 'like:user:' .. entityUserId, -1)
                if v and v < 0 then
                  redis.call('SET', 'like:user:' .. entityUserId, 0)
                end
              end
              return 1
            end
            return 0
            """,
            Long.class
    );

    private static final DefaultRedisScript<Long> DELETE_ENTITY_LIKES_SCRIPT = new DefaultRedisScript<>(
            """
            local entityKey = KEYS[1]
            local ownerKey = KEYS[2]
            local members = redis.call('SMEMBERS', entityKey)
            local count = #members
            if count > 0 then
              for i, actorUserId in ipairs(members) do
                local entityUserId = redis.call('HGET', ownerKey, actorUserId)
                if entityUserId and entityUserId ~= '' then
                  local v = redis.call('INCRBY', 'like:user:' .. entityUserId, -1)
                  if v and v < 0 then
                    redis.call('SET', 'like:user:' .. entityUserId, 0)
                  end
                end
              end
              redis.call('DEL', entityKey)
              redis.call('DEL', ownerKey)
              return count
            end
            redis.call('DEL', ownerKey)
            return 0
            """,
            Long.class
    );

    private static final String INCREMENT_USER_LIKE_COUNT_SCRIPT = """
            local userKey = KEYS[1]
            local delta = tonumber(ARGV[1])
            local v = redis.call('INCRBY', userKey, delta)
            if v and v < 0 then
              redis.call('SET', userKey, 0)
              return 0
            end
            return v or 0
            """;

    private static final DefaultRedisScript<Long> INCREMENT_USER_LIKE_COUNT_REDIS_SCRIPT = new DefaultRedisScript<>(
            INCREMENT_USER_LIKE_COUNT_SCRIPT,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public RedisLikeRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean addLike(UUID userId, int entityType, UUID entityId) {
        return addLike(userId, entityType, entityId, null);
    }

    @Override
    public boolean addLike(UUID userId, int entityType, UUID entityId, UUID entityUserId) {
        Long added = redisTemplate.opsForSet().add(entityKey(entityType, entityId), String.valueOf(userId));
        if (added != null && added > 0 && entityUserId != null) {
            redisTemplate.opsForHash().put(ownerKey(entityType, entityId), String.valueOf(userId), String.valueOf(entityUserId));
        }
        return added != null && added > 0;
    }

    @Override
    public boolean removeLike(UUID userId, int entityType, UUID entityId) {
        Long removed = redisTemplate.opsForSet().remove(entityKey(entityType, entityId), String.valueOf(userId));
        if (removed != null && removed > 0) {
            redisTemplate.opsForHash().delete(ownerKey(entityType, entityId), String.valueOf(userId));
        }
        return removed != null && removed > 0;
    }

    @Override
    public Optional<LikeRelation> findLike(UUID userId, int entityType, UUID entityId) {
        if (!isLiked(userId, entityType, entityId)) {
            return Optional.empty();
        }
        Object owner = redisTemplate.opsForHash().get(ownerKey(entityType, entityId), String.valueOf(userId));
        UUID entityUserId = owner == null ? null : UUID.fromString(String.valueOf(owner));
        return Optional.of(new LikeRelation(userId, entityType, entityId, entityUserId));
    }

    @Override
    public long deleteLikesByEntity(int entityType, UUID entityId) {
        String key = entityKey(entityType, entityId);
        Long removed = redisTemplate.execute(DELETE_ENTITY_LIKES_SCRIPT, List.of(key, ownerKey(entityType, entityId)));
        return removed == null ? 0 : removed;
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
        Long v = redisTemplate.execute(
                INCREMENT_USER_LIKE_COUNT_REDIS_SCRIPT,
                List.of(userKey(userId)),
                String.valueOf(delta)
        );
        return v == null ? 0 : v;
    }

    @Override
    public long resetUserLikeCount(UUID userId, long likeCount) {
        long normalized = Math.max(0, likeCount);
        redisTemplate.opsForValue().set(userKey(userId), String.valueOf(normalized));
        return normalized;
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
        String ownerKey = ownerKey(entityType, entityId);
        Long changed = redisTemplate.execute(
                SET_LIKE_SCRIPT,
                List.of(entityKey, userKey, ownerKey),
                String.valueOf(actorUserId),
                liked ? "1" : "0",
                String.valueOf(entityUserId)
        );
        return changed != null && changed > 0;
    }

    private String entityKey(int entityType, UUID entityId) {
        return "like:entity:" + entityType + ":" + entityId;
    }

    private String userKey(UUID userId) {
        return "like:user:" + userId;
    }

    private String ownerKey(int entityType, UUID entityId) {
        return "like:entity-owner:" + entityType + ":" + entityId;
    }
}
