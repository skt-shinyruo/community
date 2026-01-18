package com.nowcoder.community.social.like;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "social.storage", havingValue = "redis", matchIfMissing = true)
public class RedisLikeRepository implements LikeRepository {

    private final StringRedisTemplate redisTemplate;

    public RedisLikeRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean addLike(int userId, int entityType, int entityId) {
        Long added = redisTemplate.opsForSet().add(entityKey(entityType, entityId), String.valueOf(userId));
        return added != null && added > 0;
    }

    @Override
    public boolean removeLike(int userId, int entityType, int entityId) {
        Long removed = redisTemplate.opsForSet().remove(entityKey(entityType, entityId), String.valueOf(userId));
        return removed != null && removed > 0;
    }

    @Override
    public boolean isLiked(int userId, int entityType, int entityId) {
        Boolean member = redisTemplate.opsForSet().isMember(entityKey(entityType, entityId), String.valueOf(userId));
        return member != null && member;
    }

    @Override
    public long countEntityLikes(int entityType, int entityId) {
        Long size = redisTemplate.opsForSet().size(entityKey(entityType, entityId));
        return size == null ? 0 : size;
    }

    @Override
    public long incrementUserLikeCount(int userId, long delta) {
        Long v = redisTemplate.opsForValue().increment(userKey(userId), delta);
        return v == null ? 0 : v;
    }

    @Override
    public long getUserLikeCount(int userId) {
        String v = redisTemplate.opsForValue().get(userKey(userId));
        if (v == null || v.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String entityKey(int entityType, int entityId) {
        return "like:entity:" + entityType + ":" + entityId;
    }

    private String userKey(int userId) {
        return "like:user:" + userId;
    }
}

